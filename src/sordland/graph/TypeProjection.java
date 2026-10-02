package sordland.graph;

import java.util.*;
import static sordland.graph.Graph.*;

public final class TypeProjection {
    private TypeProjection() {
    }
    public static String category(Node node) {
        if (node.kind == Kind.CHOICE) return "Choice";
        if (node.kind == Kind.CONDITION) return "Condition";
        if (node.kind == Kind.EVENT) return node.type;
        if (node.kind == Kind.EFFECT) return node.type.equals("News") || sordland.data.runtime.RuntimeDatabase.TYPES.containsValue(node.type) ? node.type: "Effect";
        return "";
    }
    public static List<String> types(Graph graph) {
        var types = new TreeSet<String>();
        for (Node node: graph.nodes) {
            String type = category(node);
            if (!type.isBlank() && !(graph.campaign != null && type.equals("Dialogue fragment"))) types.add(type);
        }
        return List.copyOf(types);
    }
    public static Set<String> defaults(Collection<String> types) {
        var result = new LinkedHashSet<>(types);
        result.remove("News");
        result.removeAll(sordland.data.runtime.RuntimeDatabase.TYPES.values());
        return Collections.unmodifiableSet(result);
    }
    public static Graph project(Graph canonical, Set<String> selectedTypes) {
        Objects.requireNonNull(canonical);
        Objects.requireNonNull(selectedTypes);
        var nodes = new LinkedHashMap<String, Node>();
        var hidden = new LinkedHashSet<String>();
        var outgoing = new LinkedHashMap<String, List<Edge>>();
        var incoming = new HashMap<String, Integer>();
        for (Node node: canonical.nodes) {
            nodes.put(node.id, node);
            String type = category(node);
            if (!type.isEmpty() && !selectedTypes.contains(type)) hidden.add(node.id);
        }
        if (hidden.isEmpty()) return canonical;
        for (Edge edge: canonical.edges) {
            if (!nodes.containsKey(edge.from) || !nodes.containsKey(edge.to)) throw new IllegalArgumentException("Unresolved visual edge in type projection");
            outgoing.computeIfAbsent(edge.from, k -> new ArrayList<>()).add(edge);
            incoming.merge(edge.to, 1, Integer::sum);
        }
        var useful = new HashSet<String>();
        var reverse = new HashMap<String, List<String>>();
        for (Edge edge: canonical.edges) reverse.computeIfAbsent(edge.to, k -> new ArrayList<>()).add(edge.from);
        var pending = new ArrayDeque<String>();
        for (Node node: canonical.nodes) if (!hidden.contains(node.id)) pending.add(node.id);
        while (!pending.isEmpty()) {
            String id = pending.removeFirst();
            if (useful.add(id)) pending.addAll(reverse.getOrDefault(id, List.of()));
        }
        var anchors = new LinkedHashSet<String>();
        for (String id: hidden) {
            if (!useful.contains(id)) continue;
            long out = outgoing.getOrDefault(id, List.of()).stream().filter(e -> useful.contains(e.to)).count();
            if (out>1 || incoming.getOrDefault(id, 0)>1) anchors.add(id);
        }
        var settled = new HashSet<String>();
        for (String start: hidden) {
            if (!useful.contains(start) || anchors.contains(start) || settled.contains(start)) continue;
            var path = new LinkedHashSet<String>();
            String id = start;
            while (hidden.contains(id) && useful.contains(id) && !anchors.contains(id) && !settled.contains(id)) {
                if (!path.add(id)) {
                    anchors.add(id);
                    break;
                }
                var next = outgoing.getOrDefault(id, List.of()).stream().filter(e -> useful.contains(e.to)).toList();
                if (next.size() != 1) break;
                id = next.getFirst().to;
            }
            settled.addAll(path);
        }
        var visible = new ArrayList<Node>();
        var retained = new HashSet<String>();
        for (Node node: canonical.nodes) {
            if (!hidden.contains(node.id)) {
                visible.add(node);
                retained.add(node.id);
            } else if (anchors.contains(node.id)) {
                visible.add(new Node(node.id, Kind.JUNCTION, "", "", "Hidden " + category(node) + " projection anchor. Original metadata:\n" + node.metadata,
                    "Type projection", "", node.turn, null, node.source));
                retained.add(node.id);
            }
        }
        var edges = new ArrayList<Edge>();
        var omittedMembership = Collections.newSetFromMap(new IdentityHashMap<Edge, Boolean>());
        if (canonical.campaign != null) for (CampaignGroup group: canonical.campaign.groups()) {
            boolean remaining = group.eventIds().stream().anyMatch(retained::contains);
            var collapsed = new ArrayList<Edge>();
            for (String member: group.eventIds()) if (!retained.contains(member)) {
                for (Edge edge: outgoing.getOrDefault(group.entryId(), List.of())) if (edge.to.equals(member)) {
                    omittedMembership.add(edge);
                    collapsed.add(edge);
                }
                collapsed.addAll(outgoing.getOrDefault(member, List.of()).stream().filter(e -> e.to.equals(group.exitId())).toList());
            }
            if (!remaining && !collapsed.isEmpty()) edges.add(new Edge(group.entryId(), group.exitId(), "", false,
                flatten(collapsed)));
        }
        for (Node node: canonical.nodes) {
            if (!retained.contains(node.id)) continue;
            for (Edge first: outgoing.getOrDefault(node.id, List.of())) {
                if (!useful.contains(first.to) || omittedMembership.contains(first)) continue;
                var path = new ArrayList<Edge>();
                path.add(first);
                String id = first.to;
                var seen = new HashSet<String>();
                while (!retained.contains(id)) {
                    if (!seen.add(id)) throw new IllegalStateException("Unanchored type-projection cycle");
                    var next = outgoing.getOrDefault(id, List.of()).stream().filter(e -> useful.contains(e.to)).toList();
                    if (next.size() != 1) throw new IllegalStateException("Unanchored hidden branch: " + id);
                    Edge step = next.getFirst();
                    path.add(step);
                    id = step.to;
                }
                if (path.size() == 1 && !hidden.contains(first.from) && !hidden.contains(first.to)) {
                    edges.add(first);
                    continue;
                }
                var labels = new ArrayList<String>();
                for (Edge step: path) if (!step.label.isBlank()) labels.add(step.label);
                edges.add(new Edge(node.id, id, String.join(" → ", labels), path.stream().anyMatch(e -> e.back), flatten(path)));
            }
        }
        var notes = new ArrayList<>(canonical.diagnostics);
        notes.add("Types projection hides " + hidden.size() + " cards while retaining distinct source alternatives and original-edge provenance.");
        CampaignMetadata campaign = projectMetadata(canonical, retained, hidden, anchors);
        return new Graph(canonical.title, visible, edges, notes, campaign);
    }
    private static List<Edge> flatten(List<Edge> path) {
        var result = new ArrayList<Edge>();
        for (Edge edge: path) {
            if (edge.projectionPath.isEmpty()) result.add(edge);
            else result.addAll(edge.projectionPath);
        }
        return result;
    }
    private static CampaignMetadata projectMetadata(Graph canonical, Set<String> retained, Set<String> hidden, Set<String> anchors) {
        if (canonical.campaign == null) return null;
        var groups = new ArrayList<CampaignGroup>();
        for (CampaignGroup group: canonical.campaign.groups()) {
            var members = group.eventIds().stream().filter(id -> retained.contains(id) && !hidden.contains(id)).toList();
            if (members.size()>1 && group.eventIds().stream().noneMatch(anchors::contains)) groups.add(new CampaignGroup(group.id(),
                group.entryId(), group.exitId(), members));
        }
        Set<String> groupIds = new HashSet<>();
        groups.forEach(g -> groupIds.add(g.id()));
        var levels = new ArrayList<CampaignLevel>();
        for (CampaignLevel level: canonical.campaign.levels()) levels.add(new CampaignLevel(level.turn(), level.turnSourceIndex(),
            level.stepIndex(), level.entryId(), level.exitId(), level.eventIds().stream().filter(retained::contains).toList(),
            level.conditionIds().stream().filter(retained::contains).toList(), level.groupIds().stream().filter(groupIds::contains).toList()));
        var news = new ArrayList<NewsAttachment>();
        for (NewsAttachment attachment: canonical.campaign.news()) {
            var articles = attachment.newsIds().stream().filter(retained::contains).toList();
            if (retained.contains(attachment.eventId()) && retained.contains(attachment.effectId()) && !articles.isEmpty()) news.add(new NewsAttachment(attachment.eventId(),
                attachment.effectId(), articles));
        }
        var runtime = new ArrayList<NewsAttachment>();
        for (NewsAttachment attachment: canonical.campaign.runtime()) {
            var content = attachment.newsIds().stream().filter(retained::contains).toList();
            if (retained.contains(attachment.eventId()) && retained.contains(attachment.effectId()) && !content.isEmpty()) runtime.add(new NewsAttachment(attachment.eventId(),
                attachment.effectId(), content));
        }
        return new CampaignMetadata(canonical.campaign.turns(), levels, groups, news, runtime);
    }
}
