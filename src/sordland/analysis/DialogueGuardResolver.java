package sordland.analysis;

import sordland.data.Domain.*;
import java.util.*;

public final class DialogueGuardResolver {
    public enum Kind {
        LOCAL, DIRECT_CONDITION_EDGE, PROVEN_CHAIN, ALTERNATIVE_PATHS, NO_GUARD, PARTIAL, UNRESOLVED
    }
    public record Edge(EntryKey from, EntryKey to, int order, String priority, boolean connector) {
    }
    public record Predicate(EntryKey entry, String original) {
    }
    public record Choice(EntryKey decision, EntryKey option, int order) {
    }
    public record Path(VariableSyntax.Expr condition, List<Predicate> predicates, List<Edge> edges, List<Choice> choices,
        boolean complete, boolean branched, String boundary) {
        public Path {
            predicates = List.copyOf(predicates);
            edges = List.copyOf(edges);
            choices = List.copyOf(choices);
        }
        public Path(VariableSyntax.Expr condition, List<Predicate> predicates, List<Edge> edges) {
            this(condition, predicates, edges, List.of(), true, false, "");
        }
    }
    public record Resolved(Kind kind, List<Path> paths, String boundary, RouteProof.Graph graph) {
        public Resolved(Kind kind, List<Path> paths, String boundary) {
            this(kind, paths, boundary, null);
        }
        public Resolved {
            paths = List.copyOf(paths);
        }
        public boolean propagated() {
            return kind != Kind.LOCAL && kind != Kind.UNRESOLVED;
        }
        public VariableSyntax.Expr expression() {
            if (graph != null) return graph.necessaryCondition();
            return paths.stream().map(Path::condition).reduce((a, b) -> new VariableSyntax.Expr("or", "", List.of(a,
                b))).orElse(VariableSyntax.parse(""));
        }
        public String evidence() {
            if (graph != null) return graph.evidence();
            StringBuilder text = new StringBuilder("Guard proof: " + kind + "\nUpstream search boundary: " + boundary);
            int i = 0;
            for (Path path: paths) {
                text.append("\nPath ").append(++i).append(path.branched() ? " (proven route subgraph; edge order retained):": " (ordered source route):");
                text.append("\n  Complete ancestry: ").append(path.complete()).append("; ").append(path.boundary());
                for (Choice choice: path.choices()) text.append("\n  Source choice: ").append(choice.decision()).append(" -> ").append(choice.option()).append(" [order=").append(choice.order()).append("]");
                for (Predicate predicate: path.predicates()) text.append("\n  ").append(predicate.entry()).append(" conditionsString: ").append(predicate.original());
                for (Edge edge: path.edges()) text.append("\n  ").append(edge.from()).append(" -> ").append(edge.to()).append(" [order=").append(edge.order()).append(", priority=").append(edge.priority()).append(", connector=").append(edge.connector()).append("]");
            }
            return text.toString();
        }
    }
    private final NumericRouteResolver numericRoutes;
    public Resolved resolveNumericRoutes(EntryKey key) {
        return numericRoutes.resolve(key);
    }
    private final Map<EntryKey, Entry> entries = new HashMap<>();
    private final Map<EntryKey, List<Edge>>incoming = new HashMap<>();
    private final Map<EntryKey, Resolved> cache = new HashMap<>();
    private static final int MAX_DEPTH = 48, MAX_PATHS = 32, MAX_VISITS = 256;
    private record Walk(List<Path> paths, boolean cycle, String boundary) {
    }
    private static final class Budget {
        int visits;
    }
    public DialogueGuardResolver(Dataset data) {
        numericRoutes = new NumericRouteResolver(data);
        data.conversations().values().forEach(c -> c.entries().values().forEach(e -> entries.put(e.key(), e)));
        for (Entry entry: entries.values()) for (Link link: entry.links()) {
            incoming.computeIfAbsent(link.target(), unused -> new ArrayList<>()).add(new Edge(entry.key(), link.target(),
                link.order(), link.priority(), link.connector()));
        }
        incoming.values().forEach(list -> list.sort(Comparator.comparing((Edge edge) -> edge.from().conversationId()).thenComparing(edge -> edge.from().dialogueId()).thenComparingInt(Edge::order)));
    }
    public Resolved resolve(EntryKey key) {
        return cache.computeIfAbsent(key, this::build);
    }
    private Resolved build(EntryKey key) {
        Entry entry = entries.get(key);
        if (entry == null) return new Resolved(Kind.UNRESOLVED, List.of(), "Missing entry");
        VariableSyntax.Expr local = VariableSyntax.parse(entry.condition());
        if (!local.known()) return new Resolved(Kind.UNRESOLVED, List.of(), "Unsupported local predicate");
        Walk walk = predecessors(key, new LinkedHashSet<>(Set.of(key)), new Budget(), 0);
        boolean allGuarded = !walk.cycle() && !walk.paths().isEmpty() && walk.paths().stream().allMatch(p -> !p.predicates().isEmpty());
        if (!allGuarded) {
            List<Path> paths = entry.condition().isBlank() ? List.of(): List.of(new Path(local, List.of(new Predicate(key,
                entry.condition())), List.of()));
            return new Resolved(paths.isEmpty() ? Kind.UNRESOLVED: Kind.LOCAL, paths, walk.boundary());
        }
        var paths = new ArrayList<Path>();
        for (Path path: walk.paths()) paths.add(appendCondition(path, entry));
        Kind kind = paths.size()>1 ? Kind.ALTERNATIVE_PATHS: paths.getFirst().edges().size() == 1 ? Kind.DIRECT_CONDITION_EDGE: Kind.PROVEN_CHAIN;
        return new Resolved(kind, paths, walk.boundary());
    }
    private Walk predecessors(EntryKey key, Set<EntryKey> stack, Budget budget, int depth) {
        VariableIndex.checkCancelled();
        if (++budget.visits> MAX_VISITS || depth> MAX_DEPTH) return stop("Proof budget exceeded", true);
        Entry target = entries.get(key);
        if (target == null || key.dialogueId() == 0 || target.title().equalsIgnoreCase("START")) return stop("Entry/root boundary",
            false);
        List<Edge> edges = incoming.getOrDefault(key, List.of());
        if (edges.isEmpty()) return stop("No encoded predecessor", false);
        var paths = new ArrayList<Path>();
        String boundary = "All encoded incoming paths covered";
        for (Edge edge: edges) {
            Entry predecessor = entries.get(edge.from());
            if (edge.connector() || edge.from().conversationId() != key.conversationId() ||(!edge.priority().isBlank() &&
                !edge.priority().equalsIgnoreCase("Normal"))) return stop("Connector, cross-conversation or priority boundary",
                false);
            if (stack.contains(edge.from())) return stop("Cycle encountered", true);
            if (predecessor == null || !transparent(predecessor)) return stop("Speech, choice, effect or unsupported-script boundary at " + edge.from(),
                false);
            if (!predecessor.condition().isBlank() && predecessor.links().size() != 1) return stop("Condition has multiple continuations at " + edge.from(),
                false);
            if (!VariableSyntax.parse(predecessor.condition()).known()) return stop("Unsupported predecessor condition at " + edge.from(),
                false);
            stack.add(edge.from());
            Walk prefix = predecessors(edge.from(), stack, budget, depth + 1);
            stack.remove(edge.from());
            if (prefix.cycle()) return prefix;
            boundary = prefix.boundary();
            for (Path path: prefix.paths()) {
                Path guarded = appendCondition(path, predecessor);
                var route = new ArrayList<>(guarded.edges());
                route.add(edge);
                paths.add(new Path(guarded.condition(), guarded.predicates(), route));
                if (paths.size()> MAX_PATHS) return stop("Alternative path limit exceeded", true);
            }
        }
        if (paths.stream().anyMatch(p -> p.predicates().isEmpty())) return stop("At least one incoming path has no proven guard",
            false);
        return new Walk(List.copyOf(paths), false, boundary);
    }
    private static Path appendCondition(Path path, Entry entry) {
        if (entry.condition().isBlank()) return path;
        var predicates = new ArrayList<>(path.predicates());
        predicates.add(new Predicate(entry.key(), entry.condition()));
        var condition = VariableSyntax.parse(entry.condition());
        return new Path(path.predicates().isEmpty() ? condition: VariableSyntax.and(path.condition(), condition),
            predicates, path.edges());
    }
    private static boolean transparent(Entry entry) {
        return entry.text().isBlank() && entry.menuText().isBlank() && entry.script().isBlank() &&(entry.sequence().isBlank() ||
            entry.sequence().trim().matches("Continue\\s*\\(\\s*\\)\\s*;?"));
    }
    private static Walk stop(String reason, boolean cycle) {
        return new Walk(List.of(new Path(VariableSyntax.parse(""), List.of(), List.of())), cycle, reason);
    }
}
