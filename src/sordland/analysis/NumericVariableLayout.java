package sordland.analysis;

import java.util.*;
import static sordland.analysis.VariableIndex.*;
import static sordland.analysis.VariableSyntax.Expr;

public final class NumericVariableLayout {
    private NumericVariableLayout() {
    }
    public enum EffectKind {
        GAIN, LOSS, ZERO, SET, OPERATION
    }
    public record Effect(EffectKind kind, String text) {
    }
    public record Reference(Occurrence occurrence, int pathIndex, DialogueGuardResolver.Path path) {
        @Override
        public int hashCode() {
            return Objects.hash(occurrence.variable(), occurrence.source().key(), occurrence.operation(), pathIndex);
        }
        @Override
        public boolean equals(Object other) {
            return other instanceof Reference r && pathIndex == r.pathIndex && occurrence.operation() == r.occurrence.operation() &&
                occurrence.variable().equals(r.occurrence.variable()) && occurrence.source().key().equals(r.occurrence.source().key());
        }
        public String id() {
            return occurrence.source().key() + ":" + occurrence.operation() +(occurrence.guardProof() != null &&
                occurrence.guardProof().graph() != null && pathIndex >= 0 ? "/route/" + occurrence.guardProof().graph().segments().get(pathIndex).id(): "/path/" + pathIndex);
        }
    }
    public record Route(String label, Expr condition, Reference reference, boolean proven) {
    }
    public record Transformation(String category, Expr before, Expr after) {
    }
    public record Region(Expr expression, List<Region> children, Set<Reference> references, List<Transformation> transformations) {
        public Region(Expr expression, List<Region> children, Set<Reference> references) {
            this(expression, children, references, children.stream().flatMap(c -> c.transformations().stream()).distinct().toList());
        }
        public Region {
            children = List.copyOf(children);
            references = Collections.unmodifiableSet(new LinkedHashSet<>(references));
            transformations = List.copyOf(transformations);
        }
        public boolean horizontal() {
            return expression.op().equals("or");
        }
        public boolean leaf() {
            return children.isEmpty();
        }
        public double columns() {
            return leaf() ? 1: horizontal() ? children.stream().mapToDouble(Region::columns).sum(): children.stream().mapToDouble(Region::columns).max().orElse(1);
        }
        public double rows() {
            return leaf() ? 1: horizontal() ? children.stream().mapToDouble(Region::rows).max().orElse(1): children.stream().mapToDouble(Region::rows).sum();
        }
    }
    public record Cell(Region region, double column, double row, double columnSpan, double rowSpan) {
    }
    public record RouteLayout(Region root, List<Cell> cells, String label) {
        public RouteLayout(Region root, List<Cell> cells) {
            this(root, cells, "");
        }
        public RouteLayout {
            cells = List.copyOf(cells);
        }
    }
    public record Compaction(Expr original, String summary, String reason, Set<Reference> references, String topology) {
        public Compaction {
            references = Collections.unmodifiableSet(new LinkedHashSet<>(references));
        }
    }
    public record BonusBlock(String id, String title, Effect effect, Occurrence occurrence, List<Route> routes, Region root,
        List<Cell> cells, List<RouteLayout> sections, List<Compaction> compactions, String targetEvidence) {
        public BonusBlock(String id, String title, Effect effect, Occurrence occurrence, List<Route> routes, Region root,
            List<Cell> cells, List<RouteLayout> sections, List<Compaction> compactions) {
            this(id, title, effect, occurrence, routes, root, cells, sections, compactions, "");
        }
        public BonusBlock(String id, String title, Effect effect, Occurrence occurrence, List<Route> routes, Region root,
            List<Cell> cells, List<RouteLayout> sections) {
            this(id, title, effect, occurrence, routes, root, cells, sections, List.of());
        }
        public BonusBlock {
            compactions = List.copyOf(compactions);
            routes = List.copyOf(routes);
            cells = List.copyOf(cells);
            sections = List.copyOf(sections);
        }
    }
    public static List<BonusBlock> build(VariableAnalyzer.Analysis analysis) {
        var blocks = new ArrayList<BonusBlock>();
        var counts = new EnumMap<EffectKind, Integer>(EffectKind.class);
        for (Occurrence o: new LinkedHashSet<>(analysis.rules())) {
            VariableIndex.checkCancelled();
            Effect effect = effect(o.effect());
            int number = counts.merge(effect.kind(), 1, Integer::sum);
            String kind = switch (effect.kind()) {
                case GAIN -> "Gain";
                case LOSS -> "Loss";
                case ZERO -> "Zero";
                case SET -> "Set";
                case OPERATION -> "Operation";
            };
            var routes = new ArrayList<Route>();
            var proof = o.guardProof();
            if (proof != null && proof.graph() != null) {
                var graph = proof.graph();
                var sections = new ArrayList<RouteLayout>();
                var allCells = new ArrayList<Cell>();
                var refs = new LinkedHashSet<Reference>();
                var causes = o.source().causes();
                Set<sordland.data.Domain.EntryKey> nearest = nearestChoices(graph, causes);
                int i = 0;
                for (var segment: graph.segments()) {
                    var path = segment.path();
                    var ref = new Reference(o, i++, path);
                    refs.add(ref);
                    String label = graph.label(segment) +(path.complete() ? "": " ⚠");
                    if (!segment.predecessors().isEmpty()) label += "\n" +(segment.predecessors().size()>3 ? "via " + segment.predecessors().size() + " incoming routes": "from " + String.join(" / ",
                        segment.predecessors()));
                    routes.add(new Route(label, path.condition(), ref, path.complete()));
                    var terms = new ArrayList<Expr>();
                    terms.add(path.condition());
                    if (causes != null) for (String node: segment.nodes()) if (nearest.contains(graph.nodes().get(node).entry())) terms.add(new Expr("trigger",
                        causes.choice(graph.nodes().get(node).entry()), List.of()));
                    Region region = factor(region(ConditionLogic.junction("and", terms), Set.of(ref)));
                    var sectionCells = new ArrayList<Cell>();
                    if (!ConditionLogic.truth(region.expression()) || path.complete()) place(region, 0, 0, 1, region.rows(),
                        sectionCells);
                    sections.add(new RouteLayout(region, sectionCells));
                    allCells.addAll(sectionCells);
                }
                sections = compactEmptySegments(factorSiblingSegments(graph, sections), o);
                if (causes != null) {
                    String activation = causes.activation(o.source());
                    if (!activation.isBlank()) {
                        Expr predicate = VariableSyntax.parse(activation);
                        if (predicate.known()) {
                            Region r = factor(region(ConditionLogic.normalize(predicate), refs));
                            var c = new ArrayList<Cell>();
                            place(r, 0, 0, 1, r.rows(), c);
                            sections.addFirst(new RouteLayout(r, c, "StoryFragment activation"));
                        }
                    }
                }
                allCells.clear();
                sections.forEach(s -> allCells.addAll(s.cells()));
                Region root = new Region(graph.necessaryCondition(), List.of(), refs);
                blocks.add(new BonusBlock(o.source().key() + ":" + o.operation(), kind + " block " + number + " (" + effect.text() + ")",
                    effect, o, routes, root, allCells, sections));
                continue;
            }
            if (proof != null && !proof.paths().isEmpty()) {
                int i = 0;
                for (var path: proof.paths()) {
                    var ref = new Reference(o, i, path);
                    routes.add(new Route("Route " +(++i), ConditionLogic.normalize(path.condition()), ref, path.complete()));
                }
            } else {
                boolean known = o.condition().known() && o.guardProof() == null;
                routes.add(new Route(known ? "Local": "Partial ⚠", known ? ConditionLogic.normalize(o.condition()): ConditionLogic.literal("true"),
                    new Reference(o, -1, null), known));
            }
            Region root = factor(group("or", routes.stream().map(r -> region(r.condition(), Set.of(r.reference()))).toList()));
            if (o.source().causes() != null) root = group("and", List.of(new Region(new Expr("trigger", o.source().causes().trigger(o.source()),
                List.of()), List.of(), root.references()), root));
            var cells = new ArrayList<Cell>();
            place(root, 0, 0, 1, root.rows(), cells);
            var sections = new ArrayList<RouteLayout>();
            for (Region section: root.horizontal() && routes.size()>1 ? root.children(): List.of(root)) {
                var sectionCells = new ArrayList<Cell>();
                place(section, 0, 0, 1, section.rows(), sectionCells);
                sections.add(new RouteLayout(section, sectionCells));
            }
            blocks.add(new BonusBlock(o.source().key() + ":" + o.operation(), kind + " block " + number + " (" + effect.text() + ")",
                effect, o, routes, root, cells, sections));
        }
        return List.copyOf(blocks);
    }
    private static ArrayList<RouteLayout> compactEmptySegments(List<RouteLayout> sections, Occurrence occurrence) {
        var result = new ArrayList<RouteLayout>();
        var unguarded = new LinkedHashSet<Reference>();
        var partial = new LinkedHashSet<Reference>();
        for (var section: sections) {
            if (!ConditionLogic.truth(section.root().expression())) result.add(section);
            else for (Reference ref: section.root().references()) {
                if (ref.path() != null && !ref.path().complete()) partial.add(ref);
                else unguarded.add(ref);
            }
        }
        if (!unguarded.isEmpty()) {
            String label = occurrence.source().causes() == null ? occurrence.source().title(): occurrence.source().causes().trigger(occurrence.source());
            Region r = new Region(new Expr("trigger", label, List.of()), List.of(), unguarded);
            r = history(r, sections.stream().filter(s -> ConditionLogic.truth(s.root().expression())).map(RouteLayout::root).toList(),
                null);
            result.add(new RouteLayout(r, List.of(new Cell(r, 0, 0, 1, 1)), "Source-mode-only provenance: " + unguarded.size() + " condition-free segments"));
        }
        if (!partial.isEmpty()) {
            Region r = new Region(ConditionLogic.literal("true"), List.of(), partial);
            r = history(r, sections.stream().filter(s -> ConditionLogic.truth(s.root().expression())).map(RouteLayout::root).toList(),
                null);
            result.add(new RouteLayout(r, List.of(), "Partial ancestry ⚠ ×" + partial.size()));
        }
        return result;
    }
    private static ArrayList<RouteLayout> factorSiblingSegments(RouteProof.Graph graph, List<RouteLayout> sections) {
        var regions = new LinkedHashMap<String, Region>();
        var incoming = new LinkedHashMap<String, Set<String>>();
        for (int i = 0; i<graph.segments().size(); i++) {
            var segment = graph.segments().get(i);
            regions.put(segment.id(), sections.get(i).root());
            incoming.put(segment.id(), new LinkedHashSet<>(segment.predecessors()));
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            var outgoing = new HashMap<String, Set<String>>();
            incoming.forEach((id, parents) -> parents.forEach(p -> outgoing.computeIfAbsent(p, k -> new LinkedHashSet<>()).add(id)));
            for (String id: new ArrayList<>(regions.keySet())) {
                var next = outgoing.getOrDefault(id, Set.of());
                if (next.size() != 1) continue;
                String to = next.iterator().next();
                if (incoming.get(to).size() != 1) continue;
                Region chained = factor(group("and", List.of(regions.get(id), regions.get(to))));
                if (chained.columns()>6 || chained.rows()>4) continue;
                regions.put(to, chained);
                incoming.put(to, new LinkedHashSet<>(incoming.get(id)));
                regions.remove(id);
                incoming.remove(id);
                changed = true;
                break;
            }
            if (changed) continue;
            var parallel = new LinkedHashMap<String, List<String>>();
            for (String id: regions.keySet()) {
                String key = incoming.get(id) + " -> " + outgoing.getOrDefault(id, Set.of());
                if (regions.get(id).references().stream().anyMatch(r -> r.path() != null && !r.path().complete())) key = "partial:" + id;
                parallel.computeIfAbsent(key, k -> new ArrayList<>()).add(id);
            }
            for (var ids: parallel.values()) if (ids.size()>1) {
                String keep = ids.getFirst();
                Region merged = factor(group("or", ids.stream().map(regions::get).toList()));
                regions.put(keep, merged);
                var removed = new HashSet<>(ids.subList(1, ids.size()));
                removed.forEach(regions::remove);
                removed.forEach(incoming::remove);
                incoming.values().forEach(parents -> {
                    if (parents.removeAll(removed)) parents.add(keep);
                });
                changed = true;
                break;
            }
        }
        var result = new ArrayList<RouteLayout>();
        for (var region: regions.values()) {
            var cells = new ArrayList<Cell>();
            place(region, 0, 0, 1, region.rows(), cells);
            result.add(new RouteLayout(region, cells));
        }
        return result;
    }
    public static Effect effect(VariableSyntax.Effect e) {
        var n = ConditionLogic.number(e.value());
        if (e.operator().equals("=")) return new Effect(EffectKind.SET, "SET = " +(n == null ? e.value().display(x -> ConditionLogic.basename(x)): n.stripTrailingZeros().toPlainString()));
        if (n != null && Set.of("+", "-").contains(e.operator())) {
            if (e.operator().equals("-")) n = n.negate();
            return new Effect(n.signum()>0 ? EffectKind.GAIN: n.signum()<0 ? EffectKind.LOSS: EffectKind.ZERO,(n.signum() >= 0 ? "+": "") + n.stripTrailingZeros().toPlainString());
        }
        return new Effect(EffectKind.OPERATION, e.operator() + "= " + e.value().display(ConditionLogic::basename));
    }
    public static Region region(Expr e, Set<Reference> refs) {
        return Set.of("and", "or").contains(e.op()) ? group(e.op(), e.children().stream().map(c -> region(c, refs)).toList()): new Region(e,
            List.of(), refs);
    }
    private static Set<Reference> union(Collection<Region> nodes) {
        var refs = new LinkedHashSet<Reference>();
        nodes.forEach(n -> refs.addAll(n.references()));
        return refs;
    }
    private static Region merge(Region a, Region b) {
        var children = new ArrayList<Region>();
        for (int i = 0; i<a.children().size(); i++) children.add(merge(a.children().get(i), b.children().get(i)));
        return new Region(a.expression(), children, union(List.of(a, b)));
    }
    static Region group(String op, List<Region> input) {
        var terms = new LinkedHashMap<Expr, Region>();
        for (Region node: input) for (Region n: node.expression().op().equals(op) ? node.children(): List.of(node)) terms.merge(n.expression(),
            n, NumericVariableLayout::merge);
        Expr simplified = ConditionLogic.junction(op, List.copyOf(terms.keySet()));
        if (simplified.op().equals("literal")) return history(new Region(simplified, List.of(), union(input)), input,
            terms.size()>1 ? new Transformation(ConditionLogic.truth(simplified) ? "Boolean tautology": "Boolean contradiction",
            new Expr(op, "", List.copyOf(terms.keySet())), simplified): null);
        terms.remove(ConditionLogic.literal(op.equals("and") ? "true": "false"));
        if (terms.isEmpty()) return new Region(ConditionLogic.literal(op.equals("and") ? "true": "false"), List.of(),
            union(input));
        if (terms.size() == 1) {
            Region r = terms.values().iterator().next();
            return history(new Region(r.expression(), r.children(), union(input)), input, null);
        }
        var children = List.copyOf(terms.values());
        return history(new Region(new Expr(op, "", children.stream().map(Region::expression).toList()), children,
            union(input)), input, null);
    }
    private static Region history(Region result, List<Region> sources, Transformation change) {
        var changes = new LinkedHashSet<Transformation>(result.transformations());
        sources.forEach(r -> changes.addAll(r.transformations()));
        if (change != null) changes.add(change);
        return new Region(result.expression(), result.children(), result.references(), List.copyOf(changes));
    }
    public static Region factor(Region node) {
        if (node.leaf()) return node;
        Region current = group(node.expression().op(), node.children().stream().map(NumericVariableLayout::factor).toList());
        current = history(new Region(current.expression(), current.children(), union(List.of(current, node))), List.of(current,
            node), null);
        if (!current.horizontal() || current.children().size()<2) return current;
        var alternatives = current.children();
        var rows = alternatives.stream().map(r -> new ArrayList<>(r.expression().op().equals("and") ? r.children(): List.of(r))).toList();
        var common = new ArrayList<Region>();
        for (Region term: rows.getFirst()) if (rows.stream().allMatch(row -> row.stream().anyMatch(t -> t.expression().equals(term.expression())))) {
            Region merged = term;
            for (int i = 1; i<rows.size(); i++) merged = merge(merged, rows.get(i).stream().filter(t -> t.expression().equals(term.expression())).findFirst().orElseThrow());
            common.add(merged);
        }
        if (common.isEmpty()) return current;
        var keys = new HashSet<Expr>();
        common.forEach(c -> keys.add(c.expression()));
        var suffix = new ArrayList<Region>();
        var first = rows.getFirst();
        for (int i = first.size() - 1; i >= 0 && keys.contains(first.get(i).expression()); i--) {
            Expr key = first.get(i).expression();
            final int offset = first.size() - 1 - i;
            if (!rows.stream().allMatch(r -> r.size()> offset && r.get(r.size() - 1 - offset).expression().equals(key))) break;
            suffix.addFirst(common.stream().filter(t -> t.expression().equals(key)).findFirst().orElseThrow());
        }
        var rest = new ArrayList<Region>();
        boolean empty = false;
        for (var row: rows) {
            row.removeIf(t -> keys.contains(t.expression()));
            if (row.isEmpty()) empty = true;
            else rest.add(group("and", row));
        }
        var result = new ArrayList<>(common.stream().filter(t -> suffix.stream().noneMatch(s -> s.expression().equals(t.expression()))).toList());
        if (!empty) result.add(factor(group("or", rest)));
        result.addAll(suffix);
        Region factored = group("and", result);
        return history(new Region(factored.expression(), factored.children(), current.references()), List.of(current,
            factored), new Transformation("factored common prefix/suffix", current.expression(), factored.expression()));
    }
    static void place(Region r, double x, double y, double w, double h, List<Cell> out) {
        if (r.leaf()) {
            out.add(new Cell(r, x, y, w, h));
            return;
        }
        double cursor = 0;
        for (Region child: r.children()) {
            double size = r.horizontal() ? w * child.columns() / r.columns(): h * child.rows() / r.rows();
            place(child, r.horizontal() ? x + cursor: x, r.horizontal() ? y: y + cursor, r.horizontal() ? size: w,
                r.horizontal() ? h: size, out);
            cursor += size;
        }
    }
    static Set<sordland.data.Domain.EntryKey> nearestChoices(RouteProof.Graph graph, CausalProvenance causes) {
        var result = new LinkedHashSet<sordland.data.Domain.EntryKey>();
        if (causes == null) return result;
        var queue = new ArrayDeque<String>();
        var seen = new HashSet<String>();
        queue.add(graph.target());
        while (!queue.isEmpty()) {
            String id = queue.remove();
            if (!seen.add(id)) continue;
            var node = graph.nodes().get(id);
            if (!causes.choice(node.entry()).isBlank()) {
                result.add(node.entry());
                continue;
            }
            for (var arc: node.incoming()) queue.add(arc.upstream());
        }
        return result;
    }
    public static String audit(BonusBlock block) {
        var text = new StringBuilder("Write occurrence -> block: " + block.id() + "\n");
        for (var compact: block.compactions()) text.append("\nEXHAUSTIVE compacted region: ").append(compact.summary().isBlank() ? "hidden (pure tautological ancestry)": compact.summary()).append("\nOriginal: ").append(compact.original()).append("\nReason: ").append(compact.reason()).append("\nFork/merge: ").append(compact.topology()).append("\nRetained references: ").append(compact.references().stream().map(Reference::id).toList()).append("\n");
        for (var section: block.sections()) for (var change: section.root().transformations()) text.append(change.category()).append(": ").append(change.before()).append(" -> ").append(change.after()).append('\n');
        for (Route route: block.routes()) {
            Reference ref = route.reference();
            var represented = new ArrayList<Integer>();
            for (int i = 0; i<block.sections().size(); i++) if (block.sections().get(i).root().references().contains(ref)) represented.add(i);
            text.append(ref.id()).append(" -> regions ").append(represented).append('\n');
            if (block.occurrence().source().causes() != null) text.append("  Trigger categories: ").append(block.occurrence().source().causes().categories(ref)).append('\n');
            if (represented.isEmpty()) throw new IllegalStateException("Lost source route " + ref.id());
            text.append("  ").append(!route.proven() ? "genuinely unsupported/unresolved; ": "");
            text.append(ConditionLogic.truth(route.condition()) ? "Source-mode-only provenance; cosmetic/non-causal dialogue": "predicates retained; duplicated/factored into shared cell where references overlap").append('\n');
            if (ref.path() != null) for (var p: ref.path().predicates()) {
                text.append("  Original predicate: ").append(p.entry()).append(" ").append(p.original()).append('\n');
                Expr raw = VariableSyntax.parse(p.original());
                if (!ConditionLogic.truth(raw) && ConditionLogic.truth(ConditionLogic.normalize(raw))) text.append("  Boolean tautology: ").append(p).append('\n');
            }
            if (ref.path() != null) text.append("  Source-mode-only provenance: ").append(ref.path().edges()).append(" choices: ").append(ref.path().choices()).append(" boundary: ").append(ref.path().boundary()).append('\n');
            var proof = block.occurrence().guardProof();
            var causes = block.occurrence().source().causes();
            if (causes != null && proof != null && proof.graph() != null && ref.pathIndex() >= 0) {
                for (String id: proof.graph().segments().get(ref.pathIndex()).nodes()) {
                    var node = proof.graph().nodes().get(id);
                    text.append("  Source-mode-only provenance / original source node: ").append(causes.entryEvidence(node.entry())).append('\n');
                }
            }
        }
        return text.toString();
    }
}
