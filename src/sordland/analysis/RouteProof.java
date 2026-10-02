package sordland.analysis;

import java.util.*;
import sordland.data.Domain.EntryKey;
import static sordland.analysis.DialogueGuardResolver.*;
import static sordland.analysis.VariableSyntax.Expr;

public final class RouteProof {
    private RouteProof() {
    }
    public enum Reason {
        UNSUPPORTED_COMMAND, CONNECTOR, CROSS_CONVERSATION, PRIORITY, CYCLE, NODE_BUDGET, DEPTH_BUDGET, UNSUPPORTED_PREDICATE, MISSING_ENTRY
    }
    public record Boundary(Reason reason, EntryKey entry, String detail) {
    }
    public record Arc(String upstream, Edge edge, Choice choice) {
    }
    public record Node(String id, EntryKey entry, Expr knownCondition, String originalCondition, List<VariableSyntax.Effect> writes,
        List<Arc> incoming, List<Boundary> boundaries) {
        public Node {
            writes = List.copyOf(writes);
            incoming = List.copyOf(incoming);
            boundaries = List.copyOf(boundaries);
        }
    }
    public record Segment(String id, List<String> nodes, List<String> predecessors, Path path) {
        public Segment {
            nodes = List.copyOf(nodes);
            predecessors = List.copyOf(predecessors);
        }
    }
    public static final class Graph {
        private final String target;
        private final Map<String, Node> nodes;
        private final Map<String, Boolean> complete = new HashMap<>();
        private final Map<String, Expr> mandatory = new HashMap<>();
        private List<Segment> segments;
        public Graph(String target, Map<String, Node> nodes) {
            this.target = target;
            this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
        }
        public String target() {
            return target;
        }
        public Map<String, Node> nodes() {
            return nodes;
        }
        public boolean complete() {
            return complete(target);
        }
        public boolean complete(String id) {
            if (complete.containsKey(id)) return complete.get(id);
            Node n = nodes.get(id);
            boolean value = n.boundaries().isEmpty() && n.incoming().stream().allMatch(a -> complete(a.upstream()));
            complete.put(id, value);
            return value;
        }
        public Expr necessaryCondition() {
            return necessary(target);
        }
        private Expr necessary(String id) {
            if (mandatory.containsKey(id)) return mandatory.get(id);
            Node n = nodes.get(id);
            Set<Expr> common = null;
            for (Arc arc: n.incoming()) {
                Node previous = nodes.get(arc.upstream());
                Expr e = historical(necessary(arc.upstream()), previous.writes(), previous.id());
                if (!previous.boundaries().isEmpty()) e = historicalVariables(e, e.variables(), previous.id());
                var terms = new LinkedHashSet<>(e.op().equals("and") ? e.children(): List.of(e));
                if (common == null) common = terms;
                else common.retainAll(terms);
            }
            var terms = new ArrayList<Expr>();
            if (common != null) terms.addAll(common);
            terms.add(n.knownCondition());
            Expr result = ConditionLogic.junction("and", terms);
            mandatory.put(id, result);
            return result;
        }
        public List<Boundary> boundaries() {
            return nodes.values().stream().flatMap(n -> n.boundaries().stream()).distinct().toList();
        }
        public Set<String> variables() {
            var result = new TreeSet<String>();
            nodes.values().forEach(n -> result.addAll(n.knownCondition().variables()));
            return result;
        }
        public List<Segment> segments() {
            if (segments != null) return segments;
            var outgoing = new HashMap<String, List<String>>();
            nodes.values().forEach(n -> n.incoming().forEach(a -> outgoing.computeIfAbsent(a.upstream(), k -> new ArrayList<>()).add(n.id())));
            var owner = new HashMap<String, String>();
            var runs = new LinkedHashMap<String, List<String>>();
            collect(target, outgoing, owner, runs);
            var later = new HashMap<String, Set<String>>();
            later.put(target, Set.of());
            var ordered = new ArrayList<>(nodes.keySet());
            Collections.reverse(ordered);
            for (String id: ordered) {
                var mutations = new HashSet<String>(later.getOrDefault(id, Set.of()));
                nodes.get(id).writes().forEach(w -> mutations.add(w.variable()));
                for (Arc a: nodes.get(id).incoming()) later.computeIfAbsent(a.upstream(), k -> new HashSet<>()).addAll(mutations);
            }
            var result = new ArrayList<Segment>();
            for (var run: runs.entrySet()) {
                var predicates = new ArrayList<Predicate>();
                var edges = new ArrayList<Edge>();
                var choices = new ArrayList<Choice>();
                var conditions = new ArrayList<Expr>();
                var boundaries = new ArrayList<Boundary>();
                var predecessors = new LinkedHashSet<String>();
                for (String id: run.getValue()) {
                    Node n = nodes.get(id);
                    if (!n.originalCondition().isBlank()) predicates.add(new Predicate(n.entry(), n.originalCondition()));
                    Expr e = n.knownCondition();
                    var affected = later.getOrDefault(id, Set.of());
                    var all = new HashSet<>(affected);
                    n.writes().forEach(w -> all.add(w.variable()));
                    if (!n.boundaries().isEmpty()) all.addAll(e.variables());
                    e = historicalVariables(e, all, n.id());
                    if (!ConditionLogic.truth(e)) conditions.add(e);
                    boundaries.addAll(n.boundaries());
                    for (Arc a: n.incoming()) {
                        edges.add(a.edge());
                        if (a.choice() != null) choices.add(a.choice());
                        if (!owner.get(a.upstream()).equals(run.getKey())) predecessors.add(owner.get(a.upstream()));
                    }
                }
                String boundary = boundaries.stream().map(b -> b.reason() + " at " + b.entry() + ": " + b.detail()).collect(java.util.stream.Collectors.joining("; "));
                Path path = new Path(ConditionLogic.junction("and", conditions), predicates, edges, choices, boundaries.isEmpty(),
                    run.getValue().stream().anyMatch(k -> nodes.get(k).incoming().size()>1), boundary);
                result.add(new Segment(run.getKey(), run.getValue(), List.copyOf(predecessors), path));
            }
            return segments = List.copyOf(result);
        }
        private void collect(String id, Map<String, List<String>>outgoing, Map<String, String> owner, LinkedHashMap<String,
            List<String>>runs) {
            if (owner.containsKey(id)) return;
            var run = new ArrayList<String>();
            String current = id;
            while (true) {
                run.addFirst(current);
                Node n = nodes.get(current);
                if (n.incoming().size() != 1) break;
                String upstream = n.incoming().getFirst().upstream();
                if (owner.containsKey(upstream) || outgoing.getOrDefault(upstream, List.of()).size() != 1) break;
                current = upstream;
            }
            String key = run.getLast();
            run.forEach(k -> owner.put(k, key));
            for (String k: run) for (Arc a: nodes.get(k).incoming()) if (!run.contains(a.upstream())) collect(a.upstream(),
                outgoing, owner, runs);
            runs.put(key, List.copyOf(run));
        }
        public String label(Segment segment) {
            Node first = nodes.get(segment.nodes().getFirst()), last = nodes.get(segment.nodes().getLast());
            return "Route " + first.entry() +(first.entry().equals(last.entry()) ? "": " → " + last.entry());
        }
        public String evidence() {
            StringBuilder out = new StringBuilder("Source route DAG; target " + nodes.get(target).entry() + "; complete=" + complete() + "\n");
            for (Node n: nodes.values()) {
                out.append("\nEntry ").append(n.entry()).append(" [").append(n.id()).append("]\n");
                if (!n.originalCondition().isBlank()) out.append("  conditionsString: ").append(n.originalCondition()).append('\n');
                for (var w: n.writes()) out.append("  write: ").append(w).append('\n');
                for (Arc a: n.incoming()) {
                    out.append("  ").append(a.edge()).append('\n');
                    if (a.choice() != null) out.append("  source choice: ").append(a.choice()).append('\n');
                }
                for (Boundary b: n.boundaries()) out.append("  stopped: ").append(b.reason()).append(" at ").append(b.entry()).append(": ").append(b.detail()).append('\n');
            }
            return out.toString();
        }
    }
    static Expr historical(Expr e, List<VariableSyntax.Effect> writes, String epoch) {
        var names = new HashSet<String>();
        writes.forEach(w -> names.add(w.variable()));
        return historicalVariables(e, names, epoch);
    }
    private static Expr historicalVariables(Expr e, Set<String> writes, String epoch) {
        if (e.op().equals("history") || Collections.disjoint(e.variables(), writes)) return e;
        if (Set.of("and", "or").contains(e.op())) return new Expr(e.op(), "", e.children().stream().map(c -> historicalVariables(c,
            writes, epoch)).toList());
        return new Expr("history", epoch, List.of(e));
    }
}
