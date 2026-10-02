package sordland.analysis;

import java.util.*;
import static sordland.analysis.NumericVariableLayout.*;
import static sordland.analysis.VariableSyntax.Expr;

public final class CompactNumericLayout {
    private CompactNumericLayout() {
    }
    public static List<BonusBlock> build(List<BonusBlock> blocks) {
        return blocks.stream().map(CompactNumericLayout::compact).toList();
    }
    private static BonusBlock compact(BonusBlock block) {
        var proof = block.occurrence().guardProof();
        var causes = block.occurrence().source().causes();
        if (proof == null || proof.graph() == null || causes == null) return legacyCompact(block);
        var graph = proof.graph();
        var refs = new LinkedHashMap<sordland.data.Domain.EntryKey, Set<Reference>>();
        for (var route: block.routes()) for (String node: graph.segments().get(route.reference().pathIndex()).nodes()) refs.computeIfAbsent(graph.nodes().get(node).entry(),
            k -> new LinkedHashSet<>()).add(route.reference());
        var choices = new LinkedHashMap<sordland.data.Domain.EntryKey, String>();
        for (var key: NumericVariableLayout.nearestChoices(graph, causes)) choices.put(key, causes.choice(key));
        try {
            var result = causes.targetReachability().reduce(graph.nodes().get(graph.target()).entry(), refs, choices);
            Region root = result.region();
            var terms = new ArrayList<Region>();
            for (var original: block.sections()) if (original.label().equals("StoryFragment activation")) terms.add(original.root());
            terms.add(root);
            root = factor(group("and", terms));
            var allRefs = new LinkedHashSet<>(block.root().references());
            root = new Region(root.expression(), root.children(), allRefs, root.transformations());
            var sections = new ArrayList<RouteLayout>();
            sections.add(layout(root));
            var summaries = new LinkedHashMap<String, Set<Reference>>();
            for (var change: result.compactions()) if (!change.summary().isBlank()) summaries.computeIfAbsent(change.summary(),
                k -> new LinkedHashSet<>()).addAll(change.references());
            summaries.forEach((label, r) -> sections.add(layout(new Region(new Expr("exhaustive", label, List.of()),
                List.of(), r))));
            return new BonusBlock(block.id(), block.title(), block.effect(), block.occurrence(), block.routes(), root,
                sections.stream().flatMap(s -> s.cells().stream()).toList(), sections, result.compactions(), result.evidence());
        } catch (TargetReachability.Unresolved unresolved) {
            var fallback = legacyCompact(block);
            var sections = new ArrayList<RouteLayout>();
            Region known = region(graph.necessaryCondition(), block.root().references());
            var terms = new ArrayList<Region>();
            terms.add(known);
            for (var original: block.sections()) if (original.label().equals("StoryFragment activation")) terms.add(original.root());
            if (!choices.isEmpty()) terms.add(group("or", choices.values().stream().map(c -> new Region(new Expr("trigger",
                c, List.of()), List.of(), block.root().references())).toList()));
            Region root = factor(group("and", terms));
            sections.add(layout(root));
            for (var section: fallback.sections()) sections.add(new RouteLayout(section.root(), section.cells(), "Source-only unresolved topology"));
            return new BonusBlock(block.id(), block.title(), block.effect(), block.occurrence(), block.routes(), root,
                sections.getFirst().cells(), sections, List.of(), "Unresolved target slice: " + unresolved.getMessage() + "; necessary conditions shown, exact DAG retained below.");
        }
    }
    private static BonusBlock legacyCompact(BonusBlock block) {
        var changes = new ArrayList<Compaction>();
        var sections = new ArrayList<RouteLayout>();
        var proof = block.occurrence().guardProof();
        if (proof != null && proof.graph() != null) {
            var graph = proof.graph();
            var regions = new LinkedHashMap<String, Region>();
            var incoming = new LinkedHashMap<String, Set<String>>();
            var causes = block.occurrence().source().causes();
            var nearest = NumericVariableLayout.nearestChoices(graph, causes);
            for (int i = 0; i<graph.segments().size(); i++) {
                var segment = graph.segments().get(i);
                var ref = block.routes().get(i).reference();
                var terms = new ArrayList<Expr>();
                terms.add(segment.path().condition());
                if (causes != null) for (String node: segment.nodes()) if (nearest.contains(graph.nodes().get(node).entry())) terms.add(new Expr("trigger",
                    causes.choice(graph.nodes().get(node).entry()), List.of()));
                Region r = region(ConditionLogic.junction("and", terms), Set.of(ref));
                regions.put(segment.id(), simplify(r, changes, "source segment " + segment.id()));
                incoming.put(segment.id(), new LinkedHashSet<>(segment.predecessors()));
            }
            boolean changed = true;
            int budget = graph.segments().size() * 2;
            while (changed && budget-->0) {
                changed = false;
                var outgoing = new HashMap<String, Set<String>>();
                incoming.forEach((id, parents) -> parents.forEach(p -> outgoing.computeIfAbsent(p, k -> new LinkedHashSet<>()).add(id)));
                var parallel = new LinkedHashMap<String, List<String>>();
                for (String id: regions.keySet()) {
                    var parents = incoming.get(id);
                    var next = outgoing.getOrDefault(id, Set.of());
                    if (parents.isEmpty() || next.isEmpty() || containsChoice(regions.get(id).expression()) || regions.get(id).references().stream().anyMatch(r -> r.path() != null &&
                        !r.path().complete())) continue;
                    String key = new TreeSet<>(parents) + " -> " + new TreeSet<>(next);
                    parallel.computeIfAbsent(key, k -> new ArrayList<>()).add(id);
                }
                for (var entry: parallel.entrySet()) if (entry.getValue().size()>1) {
                    var ids = entry.getValue();
                    String keep = ids.getFirst();
                    var children = ids.stream().map(regions::get).toList();
                    var refs = new LinkedHashSet<Reference>();
                    children.forEach(r -> refs.addAll(r.references()));
                    Region merged = new Region(new Expr("or", "", children.stream().map(Region::expression).toList()),
                        children, refs);
                    regions.put(keep, simplify(merged, changes, "fork/merge " + entry.getKey() + "; segments " + ids));
                    var removed = new HashSet<>(ids.subList(1, ids.size()));
                    removed.forEach(regions::remove);
                    removed.forEach(incoming::remove);
                    incoming.values().forEach(p -> {
                        if (p.removeAll(removed)) p.add(keep);
                    });
                    changed = true;
                    break;
                }
                if (changed) continue;
                for (String id: new ArrayList<>(regions.keySet())) {
                    var next = outgoing.getOrDefault(id, Set.of());
                    if (next.size() != 1) continue;
                    String to = next.iterator().next();
                    if (incoming.get(to).size() != 1) continue;
                    if (regions.get(id).references().stream().anyMatch(r -> r.path() != null && !r.path().complete())) continue;
                    Region chained = simplify(group("and", List.of(regions.get(id), regions.get(to))), changes, "serial " + id + " -> " + to);
                    if (chained.columns()>12 || chained.rows()>24) continue;
                    regions.put(to, chained);
                    incoming.put(to, new LinkedHashSet<>(incoming.get(id)));
                    regions.remove(id);
                    incoming.remove(id);
                    changed = true;
                    break;
                }
                if (!changed) changed = reduceClosedFork(regions, incoming, outgoing, changes);
            }
            for (var original: block.sections()) if (original.label().equals("StoryFragment activation")) sections.add(original);
            for (var r: regions.values()) sections.add(layout(r));
        } else for (var section: block.sections()) sections.add(layout(simplify(section.root(), changes, "local Boolean region")));
        var summaries = new LinkedHashMap<String, Set<Reference>>();
        for (var change: changes) if (!change.summary().isBlank()) summaries.computeIfAbsent(change.summary(), k -> new LinkedHashSet<>()).addAll(change.references());
        summaries.forEach((text, refs) -> sections.add(layout(new Region(new Expr("exhaustive", text, List.of()),
            List.of(), refs))));
        var cells = sections.stream().flatMap(s -> s.cells().stream()).toList();
        return new BonusBlock(block.id(), block.title(), block.effect(), block.occurrence(), block.routes(), block.root(),
            cells, sections, changes);
    }
    private static boolean reduceClosedFork(Map<String, Region> regions, Map<String, Set<String>>incoming, Map<String,
        Set<String>>outgoing, List<Compaction> changes) {
        for (String fork: regions.keySet()) {
            if (outgoing.getOrDefault(fork, Set.of()).size()<2) continue;
            var candidates = new LinkedHashSet<String>();
            var queue = new ArrayDeque<String>(outgoing.get(fork));
            while (!queue.isEmpty() && candidates.size()<32) {
                String n = queue.remove();
                if (candidates.add(n)) queue.addAll(outgoing.getOrDefault(n, Set.of()));
            }
            for (String merge: candidates) {
                if (incoming.get(merge).size()<2) continue;
                var interior = new LinkedHashSet<String>();
                boolean closed = true;
                for (String start: outgoing.get(fork)) if (!collectUntil(start, merge, outgoing, interior, new HashSet<>())) {
                    closed = false;
                    break;
                }
                if (!closed || interior.isEmpty() || interior.size()>32) continue;
                if (interior.stream().anyMatch(n -> incoming.get(n).stream().anyMatch(p -> !p.equals(fork) && !interior.contains(p)))) continue;
                if (interior.stream().flatMap(n -> regions.get(n).references().stream()).anyMatch(r -> r.path() != null &&
                    !r.path().complete())) continue;
                var values = new HashMap<String, Expr>();
                values.put(fork, ConditionLogic.literal("true"));
                var pending = new LinkedHashSet<>(interior);
                int size = 0;
                while (!pending.isEmpty()) {
                    boolean progress = false;
                    for (String n: new ArrayList<>(pending)) if (values.keySet().containsAll(incoming.get(n))) {
                        Expr value = ConditionLogic.junction("and", List.of(ConditionLogic.junction("or", incoming.get(n).stream().map(values::get).toList()),
                            regions.get(n).expression()));
                        size += expressionSize(value, 4096);
                        if (size>4096) break;
                        values.put(n, value);
                        pending.remove(n);
                        progress = true;
                    }
                    if (!progress || size>4096) break;
                }
                if (!pending.isEmpty() || !values.keySet().containsAll(incoming.get(merge))) continue;
                Expr cover = new Expr("or", "", incoming.get(merge).stream().map(values::get).toList());
                var proof = BooleanCoverage.prove(cover);
                if (!proof.exhaustive()) continue;
                var refs = new LinkedHashSet<Reference>();
                interior.forEach(n -> refs.addAll(regions.get(n).references()));
                changes.add(new Compaction(cover, "", proof.reason(), refs, "closed DAG fork " + fork + " -> merge " + merge + "; segments " + interior));
                String keep = interior.iterator().next();
                interior.forEach(regions::remove);
                interior.forEach(incoming::remove);
                regions.put(keep, new Region(ConditionLogic.literal("true"), List.of(), refs));
                incoming.put(keep, new LinkedHashSet<>(Set.of(fork)));
                incoming.put(merge, new LinkedHashSet<>(Set.of(keep)));
                return true;
            }
        }
        return false;
    }
    private static boolean collectUntil(String n, String merge, Map<String, Set<String>>outgoing, Set<String> nodes,
        Set<String> active) {
        if (n.equals(merge)) return true;
        if (active.contains(n) || nodes.size()>32) return false;
        if (nodes.contains(n)) return true;
        var next = outgoing.getOrDefault(n, Set.of());
        if (next.isEmpty()) return false;
        active.add(n);
        for (String child: next) if (!collectUntil(child, merge, outgoing, nodes, active)) return false;
        active.remove(n);
        nodes.add(n);
        return true;
    }
    private static int expressionSize(Expr e, int budget) {
        int size = 1;
        for (Expr c: e.children()) {
            size += expressionSize(c, budget - size);
            if (size> budget) return size;
        }
        return size;
    }
    private static RouteLayout layout(Region r) {
        var cells = new ArrayList<Cell>();
        if (!ConditionLogic.truth(r.expression())) NumericVariableLayout.place(r, 0, 0, 1, r.rows(), cells);
        return new RouteLayout(r, cells, ConditionLogic.truth(r.expression()) ? "Compacted source provenance": "");
    }
    public static Region simplify(Region r, List<Compaction> changes, String topology) {
        if (r.expression().op().equals("or") && BooleanCoverage.prove(r.expression()).exhaustive()) {
            changes.add(new Compaction(r.expression(), summary(ConditionLogic.normalize(r.expression())), BooleanCoverage.prove(r.expression()).reason(),
                r.references(), topology));
            return new Region(ConditionLogic.literal("true"), List.of(), r.references(), r.transformations());
        }
        if (r.leaf()) return r;
        var children = r.children().stream().map(c -> simplify(c, changes, topology)).toList();
        Region rebuilt = new Region(new Expr(r.expression().op(), r.expression().value(), children.stream().map(Region::expression).toList()),
            children, r.references(), r.transformations());
        Region result = factor(rebuilt);
        return result.expression().equals(rebuilt.expression()) ? result: simplify(result, changes, topology);
    }
    static String summary(Expr e) {
        var vars = new LinkedHashSet<String>();
        collect(e, vars);
        if (vars.size()<2) return "";
        String first = vars.iterator().next();
        int split = first.lastIndexOf('_');
        if (split<0) return "";
        String prefix = first.substring(0, split + 1);
        if (vars.stream().anyMatch(v -> !v.startsWith(prefix))) return "";
        if (!e.op().equals("or")) return "";
        Set<String> positives = new HashSet<>();
        boolean none = false;
        for (Expr c: e.children()) {
            if (positive(c)) positives.add(c.children().getFirst().value());
            else if (c.op().equals("and") && c.children().size() == vars.size() && c.children().stream().allMatch(x -> x.op().equals("==") &&
                x.children().getFirst().op().equals("var") && x.children().getLast().value().equals("false"))) none = true;
            else return "";
        }
        if (!none || !positives.equals(vars)) return "";
        return "[EXHAUSTIVE] " + ConditionLogic.basename(prefix.substring(0, prefix.length() - 1)) + ": " + String.join(" | ",
            vars.stream().map(v -> v.substring(prefix.length())).toList()) + " | None";
    }
    private static boolean containsChoice(Expr e) {
        return e.op().equals("trigger") || e.children().stream().anyMatch(CompactNumericLayout::containsChoice);
    }
    private static boolean positive(Expr e) {
        return e.op().equals("==") && e.children().getFirst().op().equals("var") && e.children().getLast().value().equals("true");
    }
    private static void collect(Expr e, Set<String> vars) {
        if (e.op().equals("var")) vars.add(e.value());
        e.children().forEach(c -> collect(c, vars));
    }
}
