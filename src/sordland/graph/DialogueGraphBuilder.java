package sordland.graph;

import sordland.data.Domain.*;
import sordland.graph.Graph.*;
import sordland.graph.Semantics.CommandKind;
import java.util.*;

public final class DialogueGraphBuilder {
    private record Box(Kind kind, String title, String text, String metadata) {
    }
    private record EntryVisual(String first, String last, Entry entry, Semantics.Analysis analysis) {
    }
    public Graph build(Dataset dataset, Conversation conversation) {
        Objects.requireNonNull(dataset);
        Objects.requireNonNull(conversation);
        List<String> diagnostics = new ArrayList<>();
        List<EntryKey> roots = findRoots(dataset, conversation, diagnostics);
        Set<EntryKey> primaryReachable = roots.isEmpty() ? Set.of(): reachable(dataset, roots.getFirst());
        Set<EntryKey> componentRoots = new HashSet<>(roots);
        if (!roots.isEmpty()) componentRoots.remove(roots.getFirst());
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        List<PanelGroup> panels = new ArrayList<>();
        Map<EntryKey, EntryVisual> visuals = new LinkedHashMap<>();
        Deque<EntryKey> pending = new ArrayDeque<>(roots);
        Set<EntryKey> missing = new LinkedHashSet<>();
        int unknownEntries = 0;
        while (!pending.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Dialogue construction cancelled");
            EntryKey key = pending.removeFirst();
            if (visuals.containsKey(key)) continue;
            Entry entry = dataset.entry(key);
            if (entry == null) {
                String id = boxId(key, 0);
                nodes.add(new Node(id, Kind.NOTICE, "UNRESOLVED SOURCE LINK", "Missing conversation/dialogue " + key,
                    "This exact outgoing source pointer is preserved, but its destination is absent from the supplied dump.",
                    "Unresolved", "", null, null, key));
                visuals.put(key, new EntryVisual(id, id, null, null));
                missing.add(key);
                continue;
            }
            Semantics.Analysis analysis = Semantics.analyze(entry.script(), entry.sequence());
            if (!analysis.unknown().isEmpty()) unknownEntries++;
            List<Box> boxes = boxes(entry, analysis, !primaryReachable.contains(key));
            String first = boxId(key, 0);
            String previous = null;
            for (int i = 0; i<boxes.size(); i++) {
                Box box = boxes.get(i);
                String id = boxId(key, i);
                String caption = i == 0 && componentRoots.contains(key) ? "SOURCE COMPONENT · " + box.title(): box.title();
                String actor = switch (box.kind()) {
                    case CHOICE -> "You";
                    case NARRATOR -> "Narrator";
                    case CHARACTER -> entry.speaker().isBlank() ? "UNKNOWN SPEAKER": entry.speaker();
                    default -> "";
                };
                if (box.title().equals("DECISION PANEL")) {
                    if (previous != null) edges.add(new Edge(previous, id, "", false));
                    previous = PanelGraphBuilder.append(dataset.runtime(), box.text(), id, key, nodes, edges, panels,
                        diagnostics);
                    continue;
                }
                nodes.add(new Node(id, box.kind(), caption, box.text(), box.metadata(), "Dialogue", entry.speaker(),
                    null, null, key, actor));
                if (previous != null) edges.add(new Edge(previous, id, "", false));
                previous = id;
            }
            visuals.put(key, new EntryVisual(first, previous, entry, analysis));
            for (Link link: ordered(entry.links())) pending.addLast(link.target());
        }
        int sourceLinks = 0;
        for (Map.Entry<EntryKey, EntryVisual> materialized: visuals.entrySet()) {
            EntryKey key = materialized.getKey();
            EntryVisual visual = materialized.getValue();
            if (visual.entry() == null) continue;
            for (Link link: ordered(visual.entry().links())) {
                EntryVisual targetVisual = visuals.get(link.target());
                Entry target = targetVisual.entry();
                String label = "";
                if (target != null && isChoice(target)) label = "Choice " +(link.order() + 1);
                if (target != null && !target.condition().isBlank()) label = append(label, "predicate must hold");
                if (!link.priority().isBlank() && !link.priority().equalsIgnoreCase("Normal")) label = append(label,
                    "priority " + link.priority());
                if (link.connector()) label = append(label, "source connector");
                if (link.target().conversationId() != key.conversationId()) label = append(label, "to conversation " + link.target().conversationId());
                if (visual.analysis().terminal()) label = append(label, "source link after End()");
                edges.add(new Edge(visual.last(), targetVisual.first(), label, false, key, link));
                sourceLinks++;
            }
        }
        if (!missing.isEmpty()) diagnostics.add("Exact linked destinations missing from the supplied dump: " + missing);
        if (unknownEntries>0) diagnostics.add(unknownEntries + " source entries contain unresolved commands, shown explicitly without interpreting their behavior.");
        long gatedEntries = visuals.values().stream().filter(v -> v.entry() != null && !v.entry().condition().isBlank()).count();
        if (gatedEntries>0) diagnostics.add(gatedEntries + " dialogue activation predicates have no separately encoded false destination. Exact outgoing JSON pointers are retained; source link order alone does not prove an else/fallthrough route, so no false destination is invented.");
        diagnostics.add("Complete source coverage: " +(visuals.size() - missing.size()) + " unique entries, " + sourceLinks + " exact outgoing links, " + nodes.size() + " visual boxes. Each source entry is represented once.");
        diagnostics.add("Conditions/effects are displayed but not evaluated. Source order is retained; no IF/ELSE relationship is inferred. End()/output denotes conversation control, not a campaign ending.");
        Graph result = classifyBackEdges(new Graph(conversation.title(), nodes, edges, diagnostics, null, panels));
        long back = result.edges.stream().filter(e -> e.back).count();
        if (back>0) {
            diagnostics.add(back + " source loop/back-reference connections use dashed connectors.");
            result = new Graph(result.title, result.nodes, result.edges, diagnostics, result.campaign, result.panels);
        }
        return result;
    }
    private static String boxId(EntryKey key, int index) {
        return "entry-" + key.conversationId() + "-" + key.dialogueId() + "-" + index;
    }
    private static List<Link> ordered(List<Link> links) {
        List<Link> result = new ArrayList<>(links);
        result.sort(Comparator.comparingInt(Link::order));
        return result;
    }
    private static Set<EntryKey> reachable(Dataset dataset, EntryKey root) {
        Set<EntryKey> seen = new HashSet<>();
        Deque<EntryKey> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            EntryKey key = pending.removeFirst();
            if (!seen.add(key)) continue;
            Entry entry = dataset.entry(key);
            if (entry != null) for (Link link: entry.links()) pending.addLast(link.target());
        }
        return seen;
    }
    public static Graph classifyBackEdges(Graph graph) {
        Map<String, List<Integer>>outgoing = new HashMap<>();
        for (int i = 0; i<graph.edges.size(); i++) outgoing.computeIfAbsent(graph.edges.get(i).from, ignored -> new ArrayList<>()).add(i);
        Map<String, Integer> color = new HashMap<>();
        Set<Integer> back = new HashSet<>();
        record Frame(String node, Iterator<Integer> links) {
        }
        for (Node node: graph.nodes) {
            if (color.containsKey(node.id)) continue;
            Deque<Frame> stack = new ArrayDeque<>();
            color.put(node.id, 1);
            stack.push(new Frame(node.id, outgoing.getOrDefault(node.id, List.of()).iterator()));
            while (!stack.isEmpty()) {
                Frame frame = stack.peek();
                if (!frame.links().hasNext()) {
                    color.put(frame.node(), 2);
                    stack.pop();
                    continue;
                }
                int index = frame.links().next();
                String target = graph.edges.get(index).to;
                if (color.getOrDefault(target, 0) == 1) back.add(index);
                else if (!color.containsKey(target)) {
                    color.put(target, 1);
                    stack.push(new Frame(target, outgoing.getOrDefault(target, List.of()).iterator()));
                }
            }
        }
        List<Edge> classified = new ArrayList<>(graph.edges.size());
        for (int i = 0; i<graph.edges.size(); i++) classified.add(graph.edges.get(i).withBack(back.contains(i)));
        return new Graph(graph.title, graph.nodes, classified, graph.diagnostics, graph.campaign, graph.panels);
    }
    private static List<EntryKey> findRoots(Dataset dataset, Conversation conversation, List<String> diagnostics) {
        Map<Integer, Entry> entries = conversation.entries();
        var starts = new ArrayList<Entry>();
        var incoming = new HashSet<Integer>();
        for (Entry entry: entries.values()) {
            if (entry.title().trim().equalsIgnoreCase("START")) starts.add(entry);
            for (Link link: entry.links()) if (link.target().conversationId() == conversation.id()) incoming.add(link.target().dialogueId());
        }
        var candidates = new ArrayList<Entry>(starts);
        for (Entry entry: entries.values()) if (!incoming.contains(entry.key().dialogueId()) && !starts.contains(entry)) candidates.add(entry);
        candidates.addAll(entries.values());
        var visited = new HashSet<EntryKey>();
        var roots = new ArrayList<EntryKey>();
        int primaryReachable = 0;
        for (Entry candidate: candidates) {
            if (visited.contains(candidate.key())) continue;
            roots.add(candidate.key());
            var pending = new ArrayDeque<EntryKey>();
            pending.add(candidate.key());
            while (!pending.isEmpty()) {
                EntryKey key = pending.removeFirst();
                if (!visited.add(key)) continue;
                Entry entry = dataset.entry(key);
                if (entry != null) for (Link link: entry.links()) pending.addLast(link.target());
            }
            if (roots.size() == 1) primaryReachable =(int) visited.stream().filter(k -> k.conversationId() == conversation.id() &&
                entries.containsKey(k.dialogueId())).count();
        }
        if (starts.isEmpty() && !entries.isEmpty()) diagnostics.add("No explicit START entry. Showing source roots/components without inventing an entry route.");
        if (roots.size()>1) diagnostics.add((entries.size() - primaryReachable) + " source entries are outside the first root's reachability. " + "They remain visible as disconnected source components; component roots: " + roots + ".");
        if (entries.isEmpty()) diagnostics.add("This conversation contains no dialogue entries.");
        return roots;
    }
    private static List<Box> boxes(Entry entry, Semantics.Analysis analysis, boolean detached) {
        var result = new ArrayList<Box>();
        String metadata = metadata(entry, analysis, detached);
        if (!entry.condition().isBlank()) result.add(new Box(Kind.CONDITION, "CONDITION", Semantics.conditionDisplay(entry.condition()),
            metadata));
        String text = entry.text().isBlank() ? entry.menuText(): entry.text();
        String rawTitle = entry.title().trim();
        boolean pureControl = text.isBlank();
        boolean bareEnd = pureControl && analysis.terminal() && analysis.commands().stream().allMatch(c -> c.kind() == CommandKind.COSMETIC ||
            c.kind() == CommandKind.TERMINAL);
        boolean hasSemanticBox = !entry.condition().isBlank() || analysis.commands().stream().anyMatch(c -> c.kind() != CommandKind.COSMETIC);
        if (!pureControl || !hasSemanticBox || rawTitle.equalsIgnoreCase("START") || rawTitle.equalsIgnoreCase("input") ||
            rawTitle.equalsIgnoreCase("output")) {
            Kind kind;
            String caption;
            if (pureControl) {
                kind = rawTitle.equalsIgnoreCase("output") ? Kind.TERMINAL: Kind.CONTROL;
                caption = rawTitle.isBlank() ? "CONNECTOR": rawTitle;
                text = rawTitle.equalsIgnoreCase("output") ? "Conversation output": "";
            } else if (entry.isPlayer()) {
                kind = Kind.CHOICE;
                caption = "YOU";
            } else if (entry.isNarrator()) {
                kind = Kind.NARRATOR;
                caption = "NARRATOR";
            } else {
                kind = Kind.CHARACTER;
                caption = entry.speaker().isBlank() ? "UNKNOWN SPEAKER": entry.speaker();
            }
            result.add(new Box(kind, caption, text, metadata));
        }
        for (Semantics.Command command: analysis.commands()) {
            if (command.kind() == CommandKind.COSMETIC) continue;
            if (command.kind() == CommandKind.EFFECT) result.add(new Box(Kind.EFFECT, "EFFECT · " + command.origin(),
                Semantics.conditionDisplay(command.raw()), metadata));
            else if (command.kind() == CommandKind.PANEL) result.add(new Box(Kind.CONTROL, "DECISION PANEL", command.raw(),
                metadata));
            else if (command.kind() == CommandKind.UNKNOWN) result.add(new Box(Kind.NOTICE, "UNRESOLVED COMMAND · " + command.origin(),
                command.raw(), metadata));
            else result.add(new Box(Kind.TERMINAL, "End()", "Conversation control terminal", metadata));
        }
        if (result.isEmpty()) result.add(new Box(Kind.CONTROL, rawTitle.isBlank() ? "CONNECTOR": rawTitle, "", metadata));
        if (entry.links().isEmpty() && !bareEnd && !rawTitle.equalsIgnoreCase("output")) result.add(new Box(Kind.TERMINAL,
            "NO OUTGOING SOURCE LINKS", "This source route stops here; campaign outcome is not inferred.", metadata));
        return result;
    }
    private static boolean isChoice(Entry entry) {
        return entry.isPlayer() &&(!entry.text().isBlank() || !entry.menuText().isBlank());
    }
    private static String append(String a, String b) {
        return a.isBlank() ? b: a + " · " + b;
    }
    private static String metadata(Entry entry, Semantics.Analysis analysis, boolean detached) {
        return "Source conversation/dialogue: " + entry.key() + "\nActor: " + entry.actorId() + " · " + entry.speaker() + "\nSource title: " + entry.title() + "\nCondition (original): " + entry.condition() + "\nUser script (original):\n" + entry.script() + "\nSequence (original):\n" + entry.sequence() + "\nSource outgoing links (order/priority/connector retained): " + entry.links() +(detached ? "\nSOURCE INSPECTION COMPONENT: not reachable from the selected conversation root.": "") + "\nKnown presentation commands: " + analysis.cosmetic() + "\nUnknown commands: " + analysis.unknown();
    }
}
