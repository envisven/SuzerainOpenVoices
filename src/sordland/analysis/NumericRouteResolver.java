package sordland.analysis;

import sordland.data.Domain.*;
import sordland.graph.Semantics;
import java.util.*;
import static sordland.analysis.DialogueGuardResolver.*;
import static sordland.analysis.RouteProof.*;

public final class NumericRouteResolver {
    private final Map<EntryKey, Entry> entries = new LinkedHashMap<>();
    private final Map<EntryKey, List<Edge>>incoming = new HashMap<>();
    private final Map<EntryKey, Resolved> cache = new HashMap<>();
    private static final int MAX_DEPTH = 256, MAX_NODES = 1024;
    private final int maxNodes;
    public NumericRouteResolver(Dataset data) {
        this(data, MAX_NODES);
    }
    public NumericRouteResolver(Dataset data, int maxNodes) {
        this.maxNodes = maxNodes;
        data.conversations().values().forEach(c -> c.entries().values().forEach(e -> entries.put(e.key(), e)));
        for (Entry e: entries.values()) for (Link l: e.links()) incoming.computeIfAbsent(l.target(), k -> new ArrayList<>()).add(new Edge(e.key(),
            l.target(), l.order(), l.priority(), l.connector()));
        incoming.values().forEach(list -> list.sort(Comparator.comparing((Edge e) -> e.from().conversationId()).thenComparing(e -> e.from().dialogueId()).thenComparingInt(Edge::order)));
    }
    public synchronized Resolved resolve(EntryKey key) {
        return cache.computeIfAbsent(key, k -> {
            var builder = new Builder();
            String target = builder.visit(k, 0);
            var graph = new Graph(target, builder.nodes);
            var paths = graph.segments().stream().map(Segment::path).toList();
            Kind kind = !graph.complete() ? Kind.PARTIAL: graph.nodes().values().stream().allMatch(n -> ConditionLogic.truth(n.knownCondition())) ? Kind.NO_GUARD: graph.nodes().values().stream().anyMatch(n -> n.incoming().size()>1) ? Kind.ALTERNATIVE_PATHS: Kind.PROVEN_CHAIN;
            String reason = graph.boundaries().isEmpty() ? "Complete same-conversation source DAG": graph.boundaries().stream().map(b -> b.reason() + " at " + b.entry() + ": " + b.detail()).collect(java.util.stream.Collectors.joining("; "));
            return new Resolved(kind, paths, reason, graph);
        });
    }
    private final class Builder {
        final Map<String, Node> nodes = new LinkedHashMap<>();
        final Map<EntryKey, String> memo = new HashMap<>();
        final Set<EntryKey> active = new HashSet<>();
        int visits, cutoffs;
        String stop(EntryKey key, Reason reason, String detail) {
            String id = key + "/boundary/" +(cutoffs++);
            Entry e = entries.get(key);
            var local = e == null || !Set.of(Reason.NODE_BUDGET, Reason.DEPTH_BUDGET).contains(reason) ? ConditionLogic.literal("true"): VariableSyntax.parse(e.condition());
            nodes.put(id, new Node(id, key, local.known() ? ConditionLogic.normalize(local): ConditionLogic.literal("true"),
                e == null ? "": e.condition(), List.of(), List.of(), List.of(new Boundary(reason, key, detail))));
            return id;
        }
        String visit(EntryKey key, int depth) {
            VariableIndex.checkCancelled();
            if (active.contains(key)) return stop(key, Reason.CYCLE, "Back edge reaches active source entry");
            if (memo.containsKey(key)) return memo.get(key);
            if (depth> MAX_DEPTH) return stop(key, Reason.DEPTH_BUDGET, "Depth limit " + MAX_DEPTH);
            if (visits++ >= maxNodes) return stop(key, Reason.NODE_BUDGET, "Node limit " + maxNodes);
            Entry e = entries.get(key);
            if (e == null) return stop(key, Reason.MISSING_ENTRY, "No source entry");
            String id = key.toString();
            active.add(key);
            var boundaries = new ArrayList<Boundary>();
            var local = VariableSyntax.parse(e.condition());
            if (!local.known()) boundaries.add(new Boundary(Reason.UNSUPPORTED_PREDICATE, key, e.condition()));
            var writes = new ArrayList<VariableSyntax.Effect>();
            for (var c: Semantics.analyze(e.script(), e.sequence()).commands()) {
                if (c.kind() == Semantics.CommandKind.COSMETIC) continue;
                var effect = c.kind() == Semantics.CommandKind.EFFECT ? VariableSyntax.effect(c.raw()): null;
                if (effect == null) boundaries.add(new Boundary(Reason.UNSUPPORTED_COMMAND, key, c.origin() + ": " + c.raw()));
                else writes.add(effect);
            }
            var arcs = new ArrayList<Arc>();
            if (boundaries.stream().noneMatch(b -> b.reason() == Reason.UNSUPPORTED_COMMAND)) for (Edge edge: incoming.getOrDefault(key,
                List.of())) {
                String upstream;
                if (edge.connector()) upstream = stop(edge.from(), Reason.CONNECTOR, edge.toString());
                else if (edge.from().conversationId() != key.conversationId()) upstream = stop(edge.from(), Reason.CROSS_CONVERSATION,
                    edge.toString());
                else if (!edge.priority().isBlank() && !edge.priority().equalsIgnoreCase("Normal")) upstream = stop(edge.from(),
                    Reason.PRIORITY, edge.toString());
                else upstream = visit(edge.from(), depth + 1);
                arcs.add(new Arc(upstream, edge, choice(edge)));
            }
            nodes.put(id, new Node(id, key, local.known() ? ConditionLogic.normalize(local): ConditionLogic.literal("true"),
                e.condition(), writes, arcs, boundaries));
            active.remove(key);
            memo.put(key, id);
            return id;
        }
    }
    private Choice choice(Edge edge) {
        Entry from = entries.get(edge.from());
        if (from == null || from.links().size()<2) return null;
        boolean choice = from.links().stream().allMatch(l -> {
            Entry option = entries.get(l.target());
            return option != null &&(option.isPlayer() || !option.menuText().isBlank());
        });
        return choice ? new Choice(edge.from(), edge.to(), edge.order()): null;
    }
}
