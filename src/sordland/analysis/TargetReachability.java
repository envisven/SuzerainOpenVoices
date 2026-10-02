package sordland.analysis;

import sordland.data.Domain.*;
import sordland.graph.Semantics;
import java.util.*;
import static sordland.analysis.VariableSyntax.Expr;
import static sordland.analysis.NumericVariableLayout.*;

public final class TargetReachability {
    public record Fork(EntryKey entry, Map<EntryKey, Boolean> children, EntryKey merge, boolean removed, boolean oneSided,
        String writes, String reason) {
        public Fork {
            children = Collections.unmodifiableMap(new LinkedHashMap<>(children));
        }
        public String evidence() {
            return "Source fork " + entry + "\n  target-reachable children: " + children + "\n  merge/postdominator: " + merge + "\n  branch-local writes: " + writes + "\n  " + reason;
        }
    }
    public record Result(Region region, List<Fork> forks, Set<EntryKey> slice, List<String> unresolved, List<Compaction> compactions,
        String sourceEvidence) {
        public String evidence() {
            return "Target-conditioned slice: " + slice.size() + " nodes\n" + String.join("\n", unresolved) + "\n" + forks.stream().map(Fork::evidence).collect(java.util.stream.Collectors.joining("\n")) + "\nOriginal target-slice entries:\n" + sourceEvidence;
        }
    }
    private final Map<EntryKey, Entry> entries;
    private final java.util.function.Function<EntryKey, String> choiceLabel;
    private final Map<EntryKey, List<EntryKey>>incoming = new HashMap<>();
    public TargetReachability(Collection<Entry> source) {
        this(source, k -> "");
    }
    public TargetReachability(Collection<Entry> source, java.util.function.Function<EntryKey, String> choiceLabel) {
        this.choiceLabel = choiceLabel;
        entries = new LinkedHashMap<>();
        for (Entry e: source) entries.put(e.key(), e);
        for (Entry e: source) for (Link l: e.links()) incoming.computeIfAbsent(l.target(), k -> new ArrayList<>()).add(e.key());
    }
    public Result reduce(EntryKey target, Map<EntryKey, Set<Reference>>references, Map<EntryKey, String> choices) {
        Result proof = reduce(target, references, choices, false, Set.of());
        Set<EntryKey> removed = new HashSet<>();
        proof.forks().stream().filter(Fork::removed).forEach(f -> removed.add(f.entry()));
        Result display = reduce(target, references, choices, true, removed);
        return new Result(display.region(), proof.forks(), proof.slice(), proof.unresolved(), proof.compactions(),
            proof.sourceEvidence());
    }
    private Result reduce(EntryKey target, Map<EntryKey, Set<Reference>>references, Map<EntryKey, String> choices,
        boolean retainChoices, Set<EntryKey> eliminated) {
        var slice = new LinkedHashSet<EntryKey>();
        var queue = new ArrayDeque<EntryKey>();
        queue.add(target);
        while (!queue.isEmpty()) {
            VariableIndex.checkCancelled();
            EntryKey key = queue.remove();
            if (slice.add(key)) queue.addAll(incoming.getOrDefault(key, List.of()));
        }
        var next = new LinkedHashMap<EntryKey, List<EntryKey>>();
        var indegree = new HashMap<EntryKey, Integer>();
        var unresolved = new ArrayList<String>();
        for (EntryKey key: slice) {
            Entry e = entries.get(key);
            if (e == null) {
                unresolved.add("Missing entry " + key);
                next.put(key, List.of());
                continue;
            }
            var children = new ArrayList<EntryKey>();
            if (!key.equals(target)) for (Link l: e.links()) if (slice.contains(l.target())) {
                children.add(l.target());
                if (l.connector() || l.target().conversationId() != key.conversationId() ||(!l.priority().isBlank() &&
                    !l.priority().equalsIgnoreCase("Normal"))) unresolved.add("Unsupported link " + key + " -> " + l);
            }
            next.put(key, List.copyOf(children));
            for (EntryKey child: children) indegree.merge(child, 1, Integer::sum);
        }
        queue.clear();
        slice.stream().filter(k -> indegree.getOrDefault(k, 0) == 0).forEach(queue::add);
        var order = new ArrayList<EntryKey>();
        while (!queue.isEmpty()) {
            EntryKey key = queue.remove();
            order.add(key);
            for (EntryKey child: next.get(key)) if (indegree.merge(child, -1, Integer::sum) == 0) queue.add(child);
        }
        if (order.size() != slice.size()) throw new Unresolved("Cycle in target slice " + target);
        if (!unresolved.isEmpty()) throw new Unresolved(String.join("; ", unresolved));
        var restrictions = new HashMap<EntryKey, Set<EntryKey>>();
        var post = new HashMap<EntryKey, Set<EntryKey>>();
        var values = new HashMap<EntryKey, Expr>();
        var unguarded = new HashMap<EntryKey, Expr>();
        var changes = new ArrayList<Compaction>();
        var forks = new ArrayList<Fork>();
        var writes = new HashMap<EntryKey, List<VariableSyntax.Effect>>();
        for (EntryKey key: order) {
            var effects = new ArrayList<VariableSyntax.Effect>();
            Entry e = entries.get(key);
            for (var command: Semantics.analyze(e.script(), e.sequence()).commands()) {
                if (command.kind() == Semantics.CommandKind.COSMETIC) continue;
                var effect = command.kind() == Semantics.CommandKind.EFFECT ? VariableSyntax.effect(command.raw()): null;
                if (effect == null) throw new Unresolved("Unsupported command at " + key + ": " + command.raw());
                effects.add(effect);
            }
            writes.put(key, effects);
        }
        Collections.reverse(order);
        for (EntryKey key: order) {
            VariableIndex.checkCancelled();
            Entry e = entries.get(key);
            List<EntryKey> children = next.get(key);
            Set<Reference> refs = references.getOrDefault(key, Set.of());
            Set<EntryKey> dominators = new LinkedHashSet<>();
            if (!children.isEmpty()) {
                dominators.addAll(post.get(children.getFirst()));
                for (EntryKey child: children) dominators.retainAll(post.get(child));
            }
            dominators.add(key);
            post.put(key, dominators);
            var alternativesForTarget = new ArrayList<Expr>();
            boolean distinct = children.stream().map(restrictions::get).distinct().count()>1;
            var routeRestrictions = new LinkedHashSet<EntryKey>();
            children.forEach(c -> routeRestrictions.addAll(restrictions.get(c)));
            if (children.size() == 1 && e.links().size()>1 && !ConditionLogic.truth(ConditionLogic.normalize(VariableSyntax.parse(entries.get(children.getFirst()).condition())))) routeRestrictions.add(key);
            restrictions.put(key, routeRestrictions);
            for (EntryKey child: children) {
                Expr value = eliminated.contains(key) ? unguarded.get(child): values.get(child);
                String label = choiceLabel.apply(child);
                if (retainChoices && distinct && !label.isBlank() && !choices.containsKey(child)) value = join("and",
                    List.of(new Expr("trigger", label, List.of()), value));
                alternativesForTarget.add(value);
            }
            Expr suffix = children.isEmpty() ? ConditionLogic.literal("true"): join("or", alternativesForTarget);
            if (!key.equals(target)) for (int i = writes.get(key).size() - 1; i >= 0; i--) suffix = ConditionLogic.normalize(substitute(suffix,
                writes.get(key).get(i), key));
            Expr guard = VariableSyntax.parse(e.condition());
            if (!guard.known()) throw new Unresolved("Unsupported predicate at " + key + ": " + e.condition());
            guard = ConditionLogic.normalize(guard);
            Expr local = guard;
            String choice = choices.get(key);
            if (choice != null && !choice.isBlank()) local = join("and", List.of(local, new Expr("trigger", choice,
                List.of())));
            Expr result = join("and", List.of(local, suffix));
            if (exprSize(result, 2500)>2500) throw new Unresolved("Target expression display budget at " + key);
            values.put(key, result);
            unguarded.put(key, choice == null || choice.isBlank() ? suffix: join("and", List.of(new Expr("trigger",
                choice, List.of()), suffix)));
            if (e.links().size()>1) {
                var surviving = new LinkedHashMap<EntryKey, Boolean>();
                e.links().forEach(l -> surviving.put(l.target(), slice.contains(l.target())));
                EntryKey merge = null;
                if (children.size()>1) {
                    var common = new LinkedHashSet<>(post.get(children.getFirst()));
                    for (EntryKey child: children) common.retainAll(post.get(child));
                    merge = common.stream().max(Comparator.comparingInt(k -> post.get(k).size())).orElse(null);
                }
                var branchNodes = new LinkedHashSet<EntryKey>();
                for (EntryKey child: children) collect(child, merge, next, branchNodes);
                var branchWrites = branchNodes.stream().flatMap(k -> writes.getOrDefault(k, List.of()).stream().map(w -> k + ": " + w)).toList();
                Expr alternatives = new Expr("or", "", children.stream().map(c -> ConditionLogic.normalize(VariableSyntax.parse(entries.get(c).condition()))).toList());
                boolean cover = children.size()>1 && BooleanCoverage.prove(alternatives).exhaustive();
                boolean removed = cover && merge != null && suffix.equals(values.get(merge));
                if (removed) changes.add(new Compaction(alternatives, CompactNumericLayout.summary(ConditionLogic.normalize(alternatives)),
                    "Target-conditioned exhaustive fork", unionRefs(branchNodes, references), "fork " + key + " -> merge " + merge + "; children " + surviving + "; branch-local writes " + branchWrites));
                boolean one = children.size() == 1 && e.links().size()>1 && !ConditionLogic.truth(ConditionLogic.normalize(VariableSyntax.parse(entries.get(children.getFirst()).condition())));
                forks.add(new Fork(key, surviving, merge, removed, one, branchWrites.toString(), removed ? "EXHAUSTIVE -> removed": one ? "ONE-SIDED -> retained on its route": branchWrites.isEmpty() ? "Alternatives retained/reduced by target formula": "State-changing alternatives transferred; not cancelled by variable name"));
            }
        }
        var roots = order.stream().filter(k -> incoming.getOrDefault(k, List.of()).stream().noneMatch(slice::contains)).map(values::get).toList();
        var allRefs = unionRefs(slice, references);
        var atomRefs = new HashMap<String, Set<Reference>>();
        for (EntryKey key: slice) {
            var ref = references.getOrDefault(key, Set.of());
            for (String variable:(!incoming.getOrDefault(key, List.of()).isEmpty() && incoming.get(key).stream().allMatch(eliminated::contains) ? Set.<String> of(): VariableSyntax.parse(entries.get(key).condition()).variables())) atomRefs.computeIfAbsent(variable,
                k -> new LinkedHashSet<>()).addAll(ref);
            String label = choiceLabel.apply(key);
            if (!label.isBlank()) atomRefs.computeIfAbsent(label, k -> new LinkedHashSet<>()).addAll(ref);
            String direct = choices.get(key);
            if (direct != null) atomRefs.computeIfAbsent(direct, k -> new LinkedHashSet<>()).addAll(ref);
        }
        Region result = displayRegion(join("or", roots), atomRefs);
        result = new Region(result.expression(), result.children(), allRefs);
        return new Result(result, List.copyOf(forks), Set.copyOf(slice), List.copyOf(unresolved), List.copyOf(changes),
            slice.stream().map(k -> {
            Entry e = entries.get(k);
            return k + "\ncondition: " + e.condition() + "\nscript: " + e.script() + "\nsequence: " + e.sequence() + "\nlinks: " + e.links();
        }).collect(java.util.stream.Collectors.joining("\n")));
    }
    private static Region displayRegion(Expr e, Map<String, Set<Reference>>byAtom) {
        if (Set.of("and", "or").contains(e.op())) {
            var children = e.children().stream().map(c -> displayRegion(c, byAtom)).toList();
            var refs = new LinkedHashSet<Reference>();
            children.forEach(c -> refs.addAll(c.references()));
            return new Region(e, children, refs);
        }
        var refs = new LinkedHashSet<Reference>();
        e.variables().forEach(v -> refs.addAll(byAtom.getOrDefault(v, Set.of())));
        if (e.op().equals("trigger")) refs.addAll(byAtom.getOrDefault(e.value(), Set.of()));
        return new Region(e, List.of(), refs);
    }
    private static Set<Reference> unionRefs(Collection<EntryKey> nodes, Map<EntryKey, Set<Reference>>refs) {
        var all = new LinkedHashSet<Reference>();
        nodes.forEach(k -> all.addAll(refs.getOrDefault(k, Set.of())));
        return all;
    }
    private static void collect(EntryKey key, EntryKey stop, Map<EntryKey, List<EntryKey>>next, Set<EntryKey> result) {
        if (key.equals(stop) || !result.add(key)) return;
        for (EntryKey child: next.getOrDefault(key, List.of())) collect(child, stop, next, result);
    }
    private static int exprSize(Expr e, int limit) {
        int n = 1;
        for (Expr child: e.children()) {
            n += exprSize(child, limit - n);
            if (n> limit) return n;
        }
        return n;
    }
    private static Expr join(String op, List<Expr> input) {
        Expr e = ConditionLogic.junction(op, input);
        if (!e.op().equals("or")) return e;
        if (BooleanCoverage.prove(e).exhaustive()) return ConditionLogic.literal("true");
        var rows = e.children().stream().map(c -> new LinkedHashSet<>(c.op().equals("and") ? c.children(): List.of(c))).toList();
        var common = new LinkedHashSet<>(rows.getFirst());
        for (var row: rows) common.retainAll(row);
        if (common.isEmpty()) return e;
        var rest = new ArrayList<Expr>();
        for (var row: rows) {
            row.removeAll(common);
            rest.add(ConditionLogic.junction("and", List.copyOf(row)));
        }
        var terms = new ArrayList<>(common);
        terms.add(join("or", rest));
        return ConditionLogic.junction("and", terms);
    }
    private static Expr substitute(Expr e, VariableSyntax.Effect w, EntryKey key) {
        if (!e.variables().contains(w.variable()) || e.op().equals("history")) return e;
        if (w.operator().equals("=") && w.value().op().equals("literal") && Set.of("true", "false").contains(w.value().value())) {
            if (e.op().equals("var") && e.value().equals(w.variable())) return w.value();
            var children = e.children().stream().map(c -> substitute(c, w, key)).toList();
            if (Set.of("==", "!=").contains(e.op()) && children.stream().allMatch(c -> c.op().equals("literal"))) return ConditionLogic.literal(Boolean.toString(children.getFirst().value().equals(children.getLast().value()) == e.op().equals("==")));
            return new Expr(e.op(), e.value(), children);
        }
        if (Set.of("and", "or").contains(e.op())) return new Expr(e.op(), e.value(), e.children().stream().map(c -> substitute(c,
            w, key)).toList());
        return new Expr("history", "after " + key + " " + w, List.of(e));
    }
    public static final class Unresolved extends RuntimeException {
        public Unresolved(String reason) {
            super(reason);
        }
    }
}
