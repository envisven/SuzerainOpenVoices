package sordland;

import sordland.data.Domain.*;
import sordland.graph.*;
import sordland.layout.LayoutEngine;
import java.util.*;
import static sordland.TestSupport.*;

final class DialogueChecks {
    private DialogueChecks() {
    }
    static void run() {
        var builder = new DialogueGraphBuilder();
        for (Dataset fixture: List.of(diamond("", "", ""), diamond("BaseGame.Flag = true;", "", ""), diamond("", "BaseGame.Ready == true",
            ""), diamond("MaybeSetPolicy(42);", "", ""))) {
            Graph graph = builder.build(fixture, fixture.conversations().get(100));
            equal(1L, spokenOccurrences(graph, 3), "Every diamond shares one exact downstream source entry, including effects/conditions/unknown commands");
            equal(2L, graph.nodes.stream().filter(n -> n.kind == Graph.Kind.CHOICE && n.title.equals("YOU")).count(),
                "Choice boxes have simple YOU captions");
            assertCanonical(graph, fixture);
            var start = graph.nodes.stream().filter(n -> new EntryKey(100, 0).equals(n.source)).findFirst().orElseThrow();
            var choices = graph.edges.stream().filter(e -> e.from.equals(start.id) && e.sourceLink != null).toList();
            equal(List.of(1, 2), choices.stream().map(e -> e.sourceTo.dialogueId()).toList(), "Exact source choice order preserved");
            check(choices.get(0).label.startsWith("Choice 1") && choices.get(1).label.startsWith("Choice 2"), "Choice number belongs to its incoming source edge");
            check(graph.nodes.stream().filter(n -> n.kind == Graph.Kind.CHOICE).allMatch(n -> n.actor.equals("You")),
                "Player membership normalized to You");
            check(graph.nodes.stream().filter(n -> n.kind == Graph.Kind.NARRATOR).allMatch(n -> n.actor.equals("Narrator")),
                "Narrator membership normalized");
            check(graph.nodes.stream().filter(n -> !isSpeech(n)).allMatch(n -> n.actor.isEmpty()), "Source mechanics have no actor filter membership");
        }
        Dataset modified = diamond("BaseGame.Flag = true;", "BaseGame.Ready == true", "UnresolvedPanel();");
        Graph logic = builder.build(modified, modified.conversations().get(100));
        check(logic.nodes.stream().anyMatch(n -> n.kind == Graph.Kind.EFFECT && n.text.equals("Flag = true")), "Dedicated effect text is preserved");
        check(logic.nodes.stream().anyMatch(n -> n.kind == Graph.Kind.CONDITION && n.text.equals("Ready == true")),
            "Dedicated condition is preserved");
        check(logic.nodes.stream().anyMatch(n -> n.kind == Graph.Kind.NOTICE && n.text.equals("UnresolvedPanel()")),
            "Unknown command remains visible");
        List<Graph.Kind> chain = logic.nodes.stream().filter(n -> new EntryKey(100, 1).equals(n.source)).map(n -> n.kind).toList();
        equal(List.of(Graph.Kind.CONDITION, Graph.Kind.CHOICE, Graph.Kind.EFFECT, Graph.Kind.NOTICE), chain, "Entry box order remains condition, speech, source commands");
        var positioned = new LayoutEngine().dialogue(logic, Set.of(), LayoutChecks.MEASURE);
        LayoutChecks.assertGeometry(positioned, "Canonical shared graph");
        LayoutChecks.assertConnectors(positioned);
        for (boolean changes: List.of(false, true)) {
            Dataset fixture = loop(changes);
            Graph graph = builder.build(fixture, fixture.conversations().get(100));
            check(graph.edges.stream().anyMatch(e -> e.back && new EntryKey(100, 1).equals(e.sourceTo)), "Source loop is a direct back-reference, regardless of effect history");
            equal(1L, spokenOccurrences(graph, 1), "Loop target materialized once");
            check(graph.nodes.stream().noneMatch(n -> n.kind == Graph.Kind.REFERENCE), "Canonical loops create no continuation portals");
            assertCanonical(graph, fixture);
        }
        Dataset cross = crossConversation();
        Graph called = builder.build(cross, cross.conversations().get(100));
        equal(1L, spokenOccurrences(called, 2), "Cross-conversation return source materialized once");
        check(called.diagnostics.stream().noneMatch(d -> d.contains("outside the first root")), "Reachability follows exact cross-conversation call/return pointers");
        var call = called.edges.stream().filter(e -> new EntryKey(200, 7).equals(e.sourceTo)).findFirst().orElseThrow();
        equal("High", call.sourceLink.priority(), "Original priority survives source edge construction");
        check(call.sourceLink.connector(), "Original connector flag survives source edge construction");
        assertCanonical(called, cross);
        Dataset sharedChoice = dataset(List.of(entry(0, 10, "START", "", "", "", "", 1, 2), entry(1, 10, "Narrator",
            "First menu", "", "", "", 3), entry(2, 10, "Narrator", "Second menu", "", "", "", 4, 3), entry(3, 5, "Player",
            "Shared response", "", "", ""), entry(4, 5, "Player", "Another response", "", "", "")));
        Graph choiceGraph = builder.build(sharedChoice, sharedChoice.conversations().get(100));
        equal(1L, choiceGraph.nodes.stream().filter(n -> new EntryKey(100, 3).equals(n.source) && n.kind == Graph.Kind.CHOICE).count(),
            "Shared player source does not duplicate when incoming choice ordinals differ");
        equal(Set.of("Choice 1", "Choice 2"), new HashSet<>(choiceGraph.edges.stream().filter(e -> new EntryKey(100,
            3).equals(e.sourceTo)).map(e -> e.label).toList()), "Each incoming edge retains its own choice ordinal");
        Graph classifiedAgain = DialogueGraphBuilder.classifyBackEdges(choiceGraph);
        for (int i = 0; i<choiceGraph.edges.size(); i++) check(choiceGraph.edges.get(i) == classifiedAgain.edges.get(i),
            "Unchanged edge classification preserves identity for arrow selection");
        assertCanonical(choiceGraph, sharedChoice);
        Dataset full = chain(73);
        Graph whole = builder.build(full, full.conversations().get(100));
        equal(73L, whole.nodes.stream().map(n -> n.source).distinct().count(), "Complete graph retains all entries without chunk boundaries");
        assertCanonical(whole, full);
        Dataset detached = dataset(List.of(entry(0, 10, "START", "", "", "", "", 1), entry(1, 10, "Narrator", "Reachable",
            "", "", ""), entry(9, 6, "Player_Italic", "Detached", "", "", "")));
        Graph inspection = builder.build(detached, detached.conversations().get(100));
        check(inspection.nodes.stream().anyMatch(n -> n.source.dialogueId() == 9 && n.title.startsWith("SOURCE COMPONENT")),
            "Detached original entries explicitly labeled for source inspection");
        check(inspection.nodes.stream().anyMatch(n -> n.source.dialogueId() == 9 && n.actor.equals("You")), "Italic player shares You filter membership");
        assertCanonical(inspection, detached);
    }
    static void realData(Dataset data) {
        DialogueGraphBuilder builder = new DialogueGraphBuilder();
        LayoutEngine layout = new LayoutEngine();
        int count = 0, boxes = 0, sourceLinks = 0, maximum = 0, maximumConversation = -1;
        for (Conversation conversation: data.conversations().values()) {
            Graph graph = builder.build(data, conversation);
            assertCanonical(graph, data);
            check(!graph.nodes.isEmpty(), "Every supplied conversation produces a complete inspectable graph");
            Set<EntryKey> shown = new HashSet<>();
            graph.nodes.forEach(n -> {
                if (n.source != null) shown.add(n.source);
            });
            for (Entry entry: conversation.entries().values()) check(shown.contains(entry.key()), "All original entries retained, including labeled disconnected source components");
            check(graph.nodes.size()<4000, "Complete canonical graph fits below former 4000-box target: " + conversation.id());
            var positioned = layout.dialogue(graph, Set.of(), new sordland.ui.TextMeasurer());
            LayoutChecks.assertGeometry(positioned, "Conversation " + conversation.id());
            LayoutChecks.assertConnectors(positioned);
            Map<String, List<LayoutEngine.Line>>choices = new LinkedHashMap<>();
            for (var line: positioned.lines) {
                Graph.Edge edge = line.edge();
                if (edge.sourceLink == null) continue;
                Entry target = data.entry(edge.sourceTo);
                if (target.isPlayer() &&(!target.text().isBlank() || !target.menuText().isBlank())) choices.computeIfAbsent(edge.from,
                    ignored -> new ArrayList<>()).add(line);
            }
            for (var siblings: choices.values()) {
                siblings.sort(Comparator.comparingInt(line -> line.edge().sourceLink.order()));
                for (int i = 1; i<siblings.size(); i++) check(siblings.get(i - 1).points().getFirst().x()<siblings.get(i).points().getFirst().x(),
                    "Source choice ports preserve outgoing order despite shared canonical targets: " + conversation.id());
            }
            if (graph.nodes.size()> maximum) {
                maximum = graph.nodes.size();
                maximumConversation = conversation.id();
            }
            boxes += graph.nodes.size();
            sourceLinks += graph.edges.stream().filter(e -> e.sourceLink != null).count();
            count++;
        }
        equal(286, count, "All supplied Sordland conversations tested");
        System.out.println("Real dialogue coverage: " + count + " complete canonical graphs; " + boxes + " boxes; " + sourceLinks + " exact source links; maximum " + maximum + " boxes (conversation " + maximumConversation + "); zero continuations.");
    }
    private static boolean isSpeech(Graph.Node n) {
        return n.kind == Graph.Kind.CHOICE || n.kind == Graph.Kind.NARRATOR || n.kind == Graph.Kind.CHARACTER;
    }
    private static long spokenOccurrences(Graph graph, int id) {
        return graph.nodes.stream().filter(n -> new EntryKey(100, id).equals(n.source) && n.kind == Graph.Kind.NARRATOR).count();
    }
    private static Map<String, Graph.Node> index(Graph graph) {
        var result = new LinkedHashMap<String, Graph.Node>();
        graph.nodes.forEach(n -> result.put(n.id, n));
        return result;
    }
    private static void assertCanonical(Graph graph, Dataset data) {
        Map<String, Graph.Node> ids = index(graph);
        equal(graph.nodes.size(), ids.size(), "Visual box IDs are unique");
        Map<EntryKey, List<Graph.Node>>groups = new LinkedHashMap<>();
        for (Graph.Node node: graph.nodes) if (node.source != null) groups.computeIfAbsent(node.source, ignored -> new ArrayList<>()).add(node);
        Map<EntryKey, List<Graph.Edge>>pointers = new LinkedHashMap<>();
        Map<String, Integer> internalIn = new HashMap<>(), internalOut = new HashMap<>(), forwardIn = new HashMap<>();
        Map<String, List<String>>forward = new HashMap<>();
        for (Graph.Edge edge: graph.edges) {
            check(ids.containsKey(edge.from) && ids.containsKey(edge.to), "Every visual connector has real endpoints");
            if (!edge.back) {
                forward.computeIfAbsent(edge.from, ignored -> new ArrayList<>()).add(edge.to);
                forwardIn.merge(edge.to, 1, Integer::sum);
            }
            if (edge.sourceLink != null) {
                equal(edge.sourceFrom, ids.get(edge.from).source, "JSON link starts at its exact source entry");
                equal(edge.sourceTo, ids.get(edge.to).source, "JSON link reaches its exact destination entry");
                equal(edge.sourceLink.target(), edge.sourceTo, "Exact source target retained in metadata");
                pointers.computeIfAbsent(edge.sourceFrom, ignored -> new ArrayList<>()).add(edge);
            } else {
                equal(ids.get(edge.from).source, ids.get(edge.to).source, "Non-source connectors belong solely to one entry's box chain");
                internalOut.merge(edge.from, 1, Integer::sum);
                internalIn.merge(edge.to, 1, Integer::sum);
            }
        }
        for (var group: groups.entrySet()) {
            Entry source = data.entry(group.getKey());
            check(source != null, "Every tested visual entry resolves to exact source data");
            equal(1L, group.getValue().stream().filter(n -> internalIn.getOrDefault(n.id, 0) == 0).count(), "EntryKey has exactly one canonical first box");
            equal(1L, group.getValue().stream().filter(n -> internalOut.getOrDefault(n.id, 0) == 0).count(), "EntryKey has exactly one canonical last box");
            check(group.getValue().stream().allMatch(n -> internalIn.getOrDefault(n.id, 0) <= 1 && internalOut.getOrDefault(n.id,
                0) <= 1), "Single source entry owns one linear box chain");
            List<Link> expected = source.links().stream().sorted(Comparator.comparingInt(Link::order)).toList();
            List<Graph.Edge> actual = pointers.getOrDefault(group.getKey(), List.of());
            equal(expected.size(), actual.size(), "Every exact outgoing source link appears once");
            for (int i = 0; i<expected.size(); i++) check(actual.get(i).sourceLink == expected.get(i), "Original Link object, order, priority and connector metadata preserved");
        }
        Deque<String> roots = new ArrayDeque<>();
        for (String id: ids.keySet()) if (forwardIn.getOrDefault(id, 0) == 0) roots.addLast(id);
        int reached = 0;
        while (!roots.isEmpty()) {
            String from = roots.removeFirst();
            reached++;
            for (String to: forward.getOrDefault(from, List.of())) if (forwardIn.merge(to, -1, Integer::sum) == 0) roots.addLast(to);
        }
        equal(ids.size(), reached, "Classified forward graph is acyclic for layout");
    }
    private static Dataset crossConversation() {
        var caller = dataset(List.of(entry(0, 10, "START", "", "", "", "", 1), entry(1, 10, "Narrator", "Call", "",
            "", ""), entry(2, 10, "Narrator", "Returned", "", "", "")));
        var entries = new LinkedHashMap<>(caller.conversations().get(100).entries());
        Entry e = entries.get(1);
        entries.put(1, new Entry(e.key(), e.actorId(), e.speaker(), e.title(), e.text(), "", "", "", "", List.of(new Link(new EntryKey(200,
            7), 0, "High", true)), Map.of()));
        Entry callee = new Entry(new EntryKey(200, 7), 10, "Narrator", "Narrator", "Callee", "", "", "", "", List.of(new Link(new EntryKey(100,
            2), 0, "Normal", false)), Map.of());
        return new Dataset(List.of(), Map.of(100, new Conversation(100, "Sordland/Caller", entries), 200, new Conversation(200,
            "Sordland/Callee", Map.of(7, callee))), List.of(), List.of());
    }
    private static Dataset diamond(String script, String condition, String sequence) {
        return dataset(List.of(entry(0, 10, "START", "", "", "", "", 1, 2), entry(1, 5, "Player", "Left response",
            condition, script, sequence, 3), entry(2, 5, "Player", "Right response", "", "", "", 3), entry(3, 10,
            "Narrator", "Shared exact source sentence", "", "", "", 4), entry(4, 10, "End", "", "", "End();", "")));
    }
    private static Dataset loop(boolean changes) {
        return dataset(List.of(entry(0, 10, "START", "", "", "", "", 1), entry(1, 10, "Narrator", "Visit again", "",
            "", "", 2), entry(2, 10, "Narrator", "Repeat", "", changes ? "BaseGame.Count += 1;": "", "", 1)));
    }
    private static Dataset chain(int count) {
        var entries = new ArrayList<Entry>();
        for (int i = 0; i<count; i++) entries.add(entry(i, 10, i == 0 ? "START": "Narrator", i == 0 ? "": "Line " + i,
            "", "", "", i + 1<count ? new int[] {
            i + 1
        }
        : new int[] {
        }));
        return dataset(entries);
    }
    private static Dataset dataset(List<Entry> entries) {
        var map = new LinkedHashMap<Integer, Entry>();
        entries.forEach(e -> map.put(e.key().dialogueId(), e));
        var c = new Conversation(100, "Sordland/Turn01/Synthetic", map);
        return new Dataset(List.of(), Map.of(100, c), List.of(), List.of());
    }
    private static Entry entry(int id, int actor, String speaker, String text, String condition, String script, String sequence,
        int...destinations) {
        List<Link> links = new ArrayList<>();
        for (int i = 0; i<destinations.length; i++) links.add(new Link(new EntryKey(100, destinations[i]), i, "Normal",
            false));
        return new Entry(new EntryKey(100, id), actor, speaker, speaker, text, "", condition, script, sequence, links,
            Map.of());
    }
}
