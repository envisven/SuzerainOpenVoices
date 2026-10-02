package sordland.graph;

import java.util.*;
import static sordland.graph.Graph.*;

public final class ActorProjection {
    private ActorProjection() {
    }
    public static List<String> actors(Graph canonical) {
        Objects.requireNonNull(canonical);
        var names = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()));
        for (Node node: canonical.nodes) if (isSpeech(node)) names.add(actor(node));
        var result = new ArrayList<String>();
        if (names.remove("You")) result.add("You");
        if (names.remove("Narrator")) result.add("Narrator");
        result.addAll(names);
        return List.copyOf(result);
    }
    public static Graph project(Graph canonical, Set<String> selectedActors) {
        Objects.requireNonNull(canonical);
        Objects.requireNonNull(selectedActors);
        var selected = new HashSet<String>();
        selectedActors.forEach(a -> selected.add(normalize(a)));
        var nodes = new LinkedHashMap<String, Node>();
        var hidden = new LinkedHashSet<String>();
        for (Node node: canonical.nodes) {
            if (nodes.putIfAbsent(node.id, node) != null) throw new IllegalArgumentException("Duplicate visual node ID: " + node.id);
            if (isSpeech(node) && !selected.contains(actor(node))) hidden.add(node.id);
        }
        if (hidden.isEmpty()) return canonical;
        var outgoing = new LinkedHashMap<String, List<Edge>>();
        var incoming = new HashMap<String, Integer>();
        for (Edge edge: canonical.edges) {
            if (!nodes.containsKey(edge.from) || !nodes.containsKey(edge.to)) throw new IllegalArgumentException("Actor filter received an unresolved visual edge: " + edge.from + " -> " + edge.to);
            outgoing.computeIfAbsent(edge.from, k -> new ArrayList<>()).add(edge);
            incoming.merge(edge.to, 1, Integer::sum);
        }
        var anchors = new LinkedHashMap<String, String>();
        for (String id: hidden) {
            int in = incoming.getOrDefault(id, 0), out = outgoing.getOrDefault(id, List.of()).size();
            if (in != 1 || out != 1) {
                String role = in == 0 && out == 0 ? "isolated source entry": in == 0 ? "source root": out == 0 ? "source endpoint": in>1 &&
                    out>1 ? "branch and merge": out>1 ? "branch": "merge";
                anchors.put(id, role);
            }
        }
        var settled = new HashSet<String>();
        for (String start: hidden) {
            if (anchors.containsKey(start) || settled.contains(start)) continue;
            var route = new LinkedHashSet<String>();
            String id = start;
            while (hidden.contains(id) && !anchors.containsKey(id) && !settled.contains(id)) {
                if (!route.add(id)) {
                    anchors.put(id, "closed source loop");
                    break;
                }
                id = outgoing.get(id).getFirst().to;
            }
            settled.addAll(route);
        }
        var visibleIds = new LinkedHashMap<String, String>();
        var visible = new ArrayList<Node>();
        var usedIds = new HashSet<>(nodes.keySet());
        for (Node node: canonical.nodes) {
            if (!hidden.contains(node.id)) {
                visible.add(node);
                visibleIds.put(node.id, node.id);
                continue;
            }
            if (!anchors.containsKey(node.id)) continue;
            String id = "actor-filter:" + node.id;
            while (!usedIds.add(id)) id = "actor-filter:" + id;
            String who = actor(node), role = anchors.get(node.id);
            String metadata = "ACTOR FILTER PROJECTION — NOT AN ADDITIONAL SOURCE MECHANIC\n" + "The speech box for " + who + " is hidden. This compact reference preserves its " + role + " and exact source routes. No spoken text is displayed.\nCanonical visual node: " + node.id + "\nSource dialogue entry: " + node.source + "\n\nOriginal entry metadata:\n" + node.metadata;
            visible.add(new Node(id, Kind.REFERENCE, "HIDDEN SPEECH JUNCTION", who + " · " + role, metadata, "Actor filter",
                "", node.turn, null, node.source, ""));
            visibleIds.put(node.id, id);
        }
        var projected = new ArrayList<Edge>();
        int bypasses = 0;
        for (Node node: canonical.nodes) {
            if (!visibleIds.containsKey(node.id)) continue;
            for (Edge first: outgoing.getOrDefault(node.id, List.of())) {
                var path = new ArrayList<Edge>();
                path.add(first);
                String destination = first.to;
                var crossed = new HashSet<String>();
                while (!visibleIds.containsKey(destination)) {
                    if (!crossed.add(destination)) throw new IllegalStateException("Unanchored hidden cycle at " + destination);
                    List<Edge> next = outgoing.getOrDefault(destination, List.of());
                    if (next.size() != 1) throw new IllegalStateException("Unanchored hidden junction at " + destination);
                    Edge edge = next.getFirst();
                    path.add(edge);
                    destination = edge.to;
                }
                String from = visibleIds.get(node.id), to = visibleIds.get(destination);
                if (path.size() == 1 && from.equals(first.from) && to.equals(first.to)) {
                    projected.add(first);
                    continue;
                }
                var labels = new ArrayList<String>();
                var hiddenNames = new LinkedHashSet<String>();
                if (hidden.contains(node.id)) hiddenNames.add(actor(node));
                for (Edge edge: path) {
                    if (!edge.label.isBlank()) labels.add(edge.label);
                    if (hidden.contains(edge.to)) hiddenNames.add(actor(nodes.get(edge.to)));
                }
                labels.add("hidden speech (" + String.join(", ", hiddenNames) + ")");
                projected.add(new Edge(from, to, String.join(" → ", labels), false, path));
                bypasses++;
            }
        }
        var notes = new ArrayList<>(canonical.diagnostics);
        notes.add("Actor filter hides " + hidden.size() + " speech boxes; " + bypasses + " projected connectors retain exact original-edge evidence. All source mechanics remain visible.");
        if (!anchors.isEmpty()) notes.add("Actor filter retains " + anchors.size() + " compact hidden-speech junctions for source branches, merges, roots, endpoints or closed cycles. These are visual references, not additional game logic.");
        return DialogueGraphBuilder.classifyBackEdges(new Graph(canonical.title, visible, projected, notes, canonical.campaign,
            canonical.panels));
    }
    private static boolean isSpeech(Node node) {
        return node.kind == Kind.CHARACTER || node.kind == Kind.NARRATOR ||(node.kind == Kind.CHOICE && !node.type.equals("Panel choice"));
    }
    private static String actor(Node node) {
        if (node.kind == Kind.CHOICE) return "You";
        if (node.kind == Kind.NARRATOR) return "Narrator";
        String value = node.actor.isBlank() ? node.speaker: node.actor;
        return value.isBlank() ? "Unknown speaker": normalize(value);
    }
    private static String normalize(String name) {
        if (name == null) return "Unknown speaker";
        String value = name.trim();
        if (value.equalsIgnoreCase("Player") || value.equalsIgnoreCase("Player_Italic") || value.equalsIgnoreCase("You")) return "You";
        if (value.equalsIgnoreCase("Narrator")) return "Narrator";
        return value;
    }
}
