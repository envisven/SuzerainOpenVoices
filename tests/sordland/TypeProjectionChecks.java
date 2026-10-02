package sordland;

import sordland.data.Domain.*;
import sordland.graph.*;
import java.util.*;
import static sordland.graph.Graph.*;
import static sordland.TestSupport.*;

final class TypeProjectionChecks {
    static void run() {
        equal(Set.of("Conversation", "Decision", "Condition"), TypeProjection.defaults(List.of("Conversation", "Decision",
            "Condition", "News")), "Every applicable type defaults ON except News");
        TypeSelection state = new TypeSelection();
        var all = List.of("Conversation", "Condition", "News");
        state.choose(all, Set.of("News"));
        equal(Set.of("News"), state.selectedFor(all), "News independent from Condition and Conversation");
        equal(Set.of("Bill", "News"), state.selectedFor(List.of("Bill", "News", "Condition")), "New categories default ON while compatible hidden choices persist");
        var saved = state.snapshot();
        state.choose(all, Set.of("Condition"));
        equal(Set.of("News"), new TypeSelection(saved).selectedFor(all), "Back snapshot restores prior multi-select choices");
        Node a = node("a", Kind.EVENT, "Conversation"), b = node("b", Kind.EVENT, "Decision"), c = node("c", Kind.EVENT,
            "Decision"), d = node("d", Kind.EVENT, "Conversation");
        Graph chain = graph(List.of(a, b, c, d), List.of(edge("a", "b", ""), edge("b", "c", ""), edge("c", "d", "")));
        Graph projected = TypeProjection.project(chain, Set.of("Conversation"));
        equal(List.of("a", "d"), projected.nodes.stream().map(n -> n.id).toList(), "Multi-node hidden chain disappears");
        equal(1, projected.edges.size(), "Hidden chain becomes one visual edge");
        equal("a", projected.edges.getFirst().from, "Projected chain source");
        equal("d", projected.edges.getFirst().to, "Projected chain destination");
        equal(3, projected.edges.getFirst().projectionPath.size(), "Every bypassed edge remains inspectable");
        equal(4, chain.nodes.size(), "Canonical graph unmodified");
        equal(3, chain.edges.size(), "Canonical source edges unmodified");
        Node condition = node("condition", Kind.CONDITION, "Condition");
        Graph branch = graph(List.of(a, condition, b, c, d), List.of(edge("a", "condition", ""), edge("condition",
            "b", "TRUE"), edge("condition", "c", "FALSE"), edge("b", "d", ""), edge("c", "d", "")));
        Graph hidden = TypeProjection.project(branch, Set.of("Conversation"));
        check(hidden.nodes.stream().noneMatch(n -> n.kind == Kind.CONDITION || n.type.equals("Decision")), "Unchecked types completely disappear as cards");
        check(hidden.nodes.stream().anyMatch(n -> n.id.equals("condition") && n.kind == Kind.JUNCTION), "Hidden branching condition retains neutral topology anchor");
        equal(List.of("TRUE", "FALSE"), hidden.edges.stream().filter(e -> e.from.equals("condition")).map(e -> e.label).toList(),
            "Distinct boolean alternatives survive immediate reconvergence");
        equal(2L, hidden.edges.stream().filter(e -> e.from.equals("condition") && e.to.equals("d")).count(), "Parallel TRUE/FALSE paths are not deduplicated");
        Graph conditionVisible = TypeProjection.project(branch, Set.of("Conversation", "Condition"));
        check(conditionVisible.nodes.contains(condition), "Condition independently selected");
        equal(2L, conditionVisible.edges.stream().filter(e -> e.from.equals("condition")).count(), "Hiding both events preserves both outcomes");
        Node news = node("news", Kind.EVENT, "News");
        Graph optional = graph(List.of(a, news, d), List.of(edge("a", "news", "enables"), edge("a", "d", "")));
        equal(List.of(a, d), TypeProjection.project(optional, Set.of("Conversation")).nodes, "Hidden News leaf removes its annotation without adding a fake onward route");
        Graph onlyNews = TypeProjection.project(optional, Set.of("News"));
        check(onlyNews.nodes.stream().noneMatch(n -> n.kind == Kind.CONDITION), "News does not imply Condition");
        check(onlyNews.nodes.contains(news), "Only-News selection retains article");
    }
    static void realData(Dataset data) {
        Graph graph = new RootedCampaignGraphBuilder().build(data, null);
        List<String> types = TypeProjection.types(graph);
        check(types.contains("News") && types.contains("Condition"), "ROOTED Types offers independent News and Condition");
        check(!types.contains("Dialogue fragment"), "ROOTED excludes Dialogue fragment category");
        Graph defaults = TypeProjection.project(graph, TypeProjection.defaults(types));
        check(defaults.nodes.stream().noneMatch(n -> n.type.equals("News")), "News annotation cards and effects are OFF by default");
        check(defaults.nodes.stream().anyMatch(n -> n.kind == Kind.CONDITION), "Conditions remain ON by default");
        check(defaults.nodes.stream().filter(n -> n.item != null).allMatch(n -> graph.nodes.contains(n)), "Projection retains exact visible Item/source node identities");
        Graph onlyNews = TypeProjection.project(graph, Set.of("News"));
        check(onlyNews.nodes.stream().noneMatch(n -> n.kind == Kind.CONDITION || n.kind == Kind.EVENT && !n.type.equals("News")),
            "Only News does not show unrelated type cards");
        check(!onlyNews.campaign.news().isEmpty(), "Only News retains placement/provenance metadata");
        for (Node node: onlyNews.nodes) check(node.kind != Kind.EVENT || node.item != null, "Every visible article opens an existing Item detail");
        Graph plain = new CampaignGraphBuilder().build(data, "", "All types", null, true);
        for (Node node: plain.nodes) if (node.type.equals("News")) {
            equal(node.item.internalName(), node.text, "PLAIN News body contains only database identity");
            check(node.title.startsWith("NEWS · "), "PLAIN News keeps compact type/title label");
        }
        Graph visible = TypeProjection.project(plain, TypeProjection.defaults(TypeProjection.types(plain)));
        check(visible.nodes.stream().noneMatch(n -> n.type.equals("News")), "PLAIN uses same default News visibility rule");
        check(visible.nodes.stream().anyMatch(n -> n.type.equals("Conditional instruction")), "Supported conditional instructions are graphical, not ignored");
    }
    static Node node(String id, Kind kind, String type) {
        return new Node(id, kind, id, id, "raw source", type, "", 1, null, null);
    }
    static Edge edge(String a, String b, String label) {
        return new Edge(a, b, label, false);
    }
    static Graph graph(List<Node> nodes, List<Edge> edges) {
        return new Graph("fixture", nodes, edges, List.of());
    }
}
