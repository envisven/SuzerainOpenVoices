package sordland;

import sordland.data.Domain.Dataset;
import sordland.graph.Graph;
import sordland.graph.Graph.*;
import sordland.graph.RootedCampaignGraphBuilder;
import sordland.graph.TypeProjection;
import sordland.layout.LayoutEngine;
import sordland.layout.LayoutEngine.*;
import sordland.ui.TextMeasurer;
import java.util.*;
import static sordland.TestSupport.*;

final class CampaignLayoutChecks {
    private CampaignLayoutChecks() {
    }
    static void run(Dataset data) {
        var engine = new LayoutEngine();
        Graph graph = new RootedCampaignGraphBuilder().build(data, null);
        var measure = new TextMeasurer();
        Result compact = engine.campaign(graph, Set.of(), measure);
        inspect(compact, "Full rooted campaign");
        Result repeated = engine.campaign(graph, Set.of(), measure);
        equal(compact.boxes, repeated.boxes, "Repeated campaign layout preserves all positions and source ordering");
        equal(compact.lines, repeated.lines, "Repeated campaign layout preserves all routes");
        equal(compact.groups, repeated.groups, "Repeated campaign layout preserves sibling enclosure geometry");
        equal(data.gameFlow().turns().size(), compact.sectors.size(), "All source turn separators remain in the continuous graph");
        check(compact.byId.get("campaign:start").y()<compact.boxes.stream().filter(b -> b.node().kind == Kind.EVENT).mapToDouble(Box::y).min().orElseThrow(),
            "START appears above the first campaign event");
        for (int i = 1; i<graph.campaign.levels().size(); i++) {
            var prior = graph.campaign.levels().get(i - 1);
            var next = graph.campaign.levels().get(i);
            double priorBottom = prior.eventIds().stream().map(compact.byId::get).mapToDouble(Box::bottom).max().orElse(0);
            double nextTop = next.eventIds().stream().map(compact.byId::get).mapToDouble(Box::y).min().orElse(Double.POSITIVE_INFINITY);
            check(priorBottom<nextTop, "GameFlow level events progress downwards in source order");
        }
        var expanded = new LinkedHashSet<String>();
        graph.nodes.stream().filter(n -> n.kind != Kind.JUNCTION).forEach(n -> expanded.add(n.id));
        inspect(engine.campaign(graph, expanded, measure), "Fully expanded rooted campaign");
        var allTypes = new LinkedHashSet<>(TypeProjection.types(graph));
        var withoutConditions = new LinkedHashSet<>(allTypes);
        withoutConditions.remove("Condition");
        var withoutConversations = new LinkedHashSet<>(allTypes);
        withoutConversations.remove("Conversation");
        for (Set<String> selection: List.of(TypeProjection.defaults(allTypes), Set.of("Conversation"), Set.of("News"),
            Set.of("News", "Condition"), withoutConditions, withoutConversations, Set.<String> of())) {
            Graph projected = TypeProjection.project(graph, selection);
            Result filtered = engine.campaign(projected, Set.of(), measure);
            inspect(filtered, "Type-projected campaign " + selection);
            for (var attachment: projected.campaign.news()) {
                Box parent = filtered.byId.get(attachment.eventId()), effect = filtered.byId.get(attachment.effectId());
                check(effect.y()> parent.bottom(), "News effect sits immediately beside/below its exact enabling event");
                var sector = filtered.sectors.stream().filter(s -> Objects.equals(s.turn(), parent.node().turn)).findFirst().orElseThrow();
                for (String id: attachment.newsIds()) {
                    Box article = filtered.byId.get(id);
                    check(article.y() >= sector.y() && article.bottom() <= sector.y() + sector.height(), "News annotation remains in the enabler's turn band");
                }
            }
            var byEndpoints = new HashMap<String, List<Line>>();
            for (Line line: filtered.lines) byEndpoints.computeIfAbsent(line.edge().from + "/" + line.edge().to, k -> new ArrayList<>()).add(line);
            for (var routes: byEndpoints.values()) if (routes.size()>1) for (int i = 1; i<routes.size(); i++) check(!routes.getFirst().points().equals(routes.get(i).points()),
                "Immediate reconverging alternatives have visibly distinct routes after type projection");
        }
        var categories = List.copyOf(allTypes);
        for (int mask = 0; mask<(1<<categories.size()); mask++) {
            var selected = new LinkedHashSet<String>();
            for (int bit = 0; bit<categories.size(); bit++) if ((mask &(1<<bit)) != 0) selected.add(categories.get(bit));
            var projection = TypeProjection.project(graph, selected);
            var result = engine.campaign(projection, Set.of(), measure);
            inspect(result, "Exhaustive Types " + selected);
            for (Node node: projection.nodes) {
                String category = TypeProjection.category(node);
                check(category.isBlank() || selected.contains(category), "Unchecked category never reappears");
            }
        }
        for (var turn: data.gameFlow().turns()) {
            Result isolated = engine.campaign(new RootedCampaignGraphBuilder().build(data, turn.turnNumber()), Set.of(),
                measure);
            inspect(isolated, "Isolated turn " + turn.turnNumber());
            equal("TURN START", isolated.graph.nodes.getFirst().title, "An isolated turn keeps its synthetic entry");
        }
        Graph fixture = fixture();
        Result wrapping = engine.campaign(fixture, Set.of(),(node, expandedNode) -> new Size(350, 78, List.of(node.title),
            List.of(), List.of()));
        inspect(wrapping, "Mixed group and condition fixture");
        equal(1, wrapping.groups.size(), "Only multiple unconditional siblings receive a container");
        Group group = wrapping.groups.getFirst();
        equal(2L, group.eventIds().stream().map(wrapping.byId::get).map(Box::y).distinct().count(), "Five wide sibling cards use the minimum two rows");
        check(group.width() <= 1500, "Wide sibling group stays within the horizontal packing limit");
        equal(null, wrapping.hit(group.x() + 2, group.y() + 2), "A sibling container is not clickable");
        check(wrapping.byId.get("conditional").x()> group.x() + group.width(), "A conditional alternative stays outside the unconditional container");
        check(wrapping.groups.stream().noneMatch(g -> g.eventIds().contains("single")), "A singleton level has no gray enclosure");
    }
    static void inspect(Result result, String label) {
        LayoutChecks.assertGeometry(result, label);
        equal(result.graph.nodes.size(), result.boxes.size(), label + " retains every source and layout node");
        for (Box box: result.boxes) {
            if (box.node().kind == Kind.JUNCTION) equal(null, result.hit(box.cx(), box.y() + box.h() / 2), label + " neutral junctions are noninteractive");
            else equal(box, result.hit(box.cx(), box.y() + box.h() / 2), label + " every event/condition remains independently clickable");
        }
        long suppressed = 0;
        for (Group group: result.groups) {
            check(group.eventIds().size()>1, label + " does not enclose a single event");
            Box entry = result.byId.get(group.entryId()), exit = result.byId.get(group.exitId());
            equal(group.cx(), entry.cx(), label + " incoming group port is top middle");
            equal(group.y(), entry.y(), label + " incoming group port is on the top border");
            equal(group.cx(), exit.cx(), label + " outgoing group port is bottom middle");
            equal(group.bottom(), exit.bottom(), label + " outgoing group port is on the bottom border");
            equal(1L, result.lines.stream().filter(line -> line.edge().to.equals(group.entryId())).count(), label + " group has a single visible incoming connector");
            equal(1L, result.lines.stream().filter(line -> line.edge().from.equals(group.exitId())).count(), label + " group has a single visible outgoing connector");
            check(result.lines.stream().noneMatch(line -> line.edge().from.equals(group.entryId()) || line.edge().to.equals(group.exitId())),
                label + " no confusing interior group membership lines are drawn");
            double left = Double.POSITIVE_INFINITY, top = Double.POSITIVE_INFINITY, right = 0, bottom = 0;
            for (String id: group.eventIds()) {
                Box member = result.byId.get(id);
                left = Math.min(left, member.x());
                top = Math.min(top, member.y());
                right = Math.max(right, member.x() + member.w());
                bottom = Math.max(bottom, member.bottom());
                check(member.x()> group.x() && member.x() + member.w()<group.x() + group.width() && member.y()> group.y() &&
                    member.bottom()<group.bottom(), label + " enclosure surrounds each member with padding");
            }
            check(left - group.x() <= 14.001 && top - group.y() <= 14.001 && group.x() + group.width() - right <= 14.001 &&
                group.bottom() - bottom <= 14.001, label + " enclosure is only slightly larger than its minimum member bounding box");
            suppressed += group.eventIds().size() * 2L;
        }
        equal(result.graph.edges.size() - suppressed,(long) result.lines.size(), label + " only purely aesthetic group membership routes are suppressed");
        for (Line line: result.lines) {
            check(line.from().bottom() <= line.to().y(), label + " every visible connector descends");
            for (int i = 1; i<line.points().size(); i++) {
                Point a = line.points().get(i - 1), b = line.points().get(i);
                check(a.x() == b.x() || a.y() == b.y(), label + " connectors use orthogonal routed segments");
                for (Box box: result.visible(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.abs(a.x() - b.x()),
                    Math.abs(a.y() - b.y()))) if (box.node().kind != Kind.JUNCTION) check(!intersects(a, b, box.x(),
                    box.y(), box.w(), box.h()), label + " connector " + line.edge().from + " -> " + line.edge().to + " segment " + a + " -> " + b + " avoids card interior " + box.node().id + " " + box.x() + "," + box.y());
                for (Group group: result.groups) check(!intersects(a, b, group.x(), group.y(), group.width(), group.height()),
                    label + " connector avoids sibling container interior");
            }
        }
    }
    private static boolean intersects(Point a, Point b, double x, double y, double width, double height) {
        return a.x() == b.x() ? a.x()> x +.01 && a.x()<x + width -.01 && Math.max(a.y(), b.y())> y +.01 && Math.min(a.y(),
            b.y())<y + height -.01: a.y()> y +.01 && a.y()<y + height -.01 && Math.max(a.x(), b.x())> x +.01 && Math.min(a.x(),
            b.x())<x + width -.01;
    }
    private static Graph fixture() {
        var nodes = new ArrayList<Node>();
        var edges = new ArrayList<Edge>();
        nodes.add(node("start", Kind.CONTROL));
        nodes.add(node("condition", Kind.CONDITION));
        nodes.add(node("gin", Kind.JUNCTION));
        nodes.add(node("gout", Kind.JUNCTION));
        nodes.add(node("join", Kind.JUNCTION));
        var members = List.of("a", "b", "c", "d", "e");
        for (String id: members) {
            nodes.add(node(id, Kind.EVENT));
            edges.add(edge("gin", id));
            edges.add(edge(id, "gout"));
        }
        nodes.add(node("conditional", Kind.EVENT));
        nodes.add(node("next", Kind.JUNCTION));
        nodes.add(node("single", Kind.EVENT));
        nodes.add(node("end", Kind.JUNCTION));
        edges.addAll(List.of(edge("start", "gin"), edge("start", "condition"), edge("condition", "conditional"), edge("conditional",
            "join"), edge("gout", "join"), edge("join", "next"), edge("next", "single"), edge("single", "end")));
        var group = new CampaignGroup("siblings", "gin", "gout", members);
        var events = new ArrayList<>(members);
        events.add("conditional");
        var levels = List.of(new CampaignLevel(1, 0, 0, "start", "join", events, List.of("condition"), List.of("siblings")),
            new CampaignLevel(1, 0, 1, "next", "end", List.of("single"), List.of(), List.of()));
        return new Graph("Mixed campaign fixture", nodes, edges, List.of(), new CampaignMetadata(List.of(new CampaignTurn(1,
            0, "Source title", "start")), levels, List.of(group)));
    }
    private static Node node(String id, Kind kind) {
        return new Node(id, kind, id, "", "", kind == Kind.EVENT ? "Conversation": "", "", 1, null, null);
    }
    private static Edge edge(String from, String to) {
        return new Edge(from, to, "", false);
    }
}
