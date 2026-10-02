package sordland;

import sordland.graph.Graph;
import sordland.layout.LayoutEngine;
import java.util.*;
import static sordland.TestSupport.*;

final class LayoutChecks {
    private LayoutChecks() {
    }
    static final LayoutEngine.Measurer MEASURE =(node, expanded) -> {
        double width = 220 +(Math.floorMod(node.id.hashCode(), 7) * 31);
        double height = 54 + Math.min(450, node.text.length() / 3.0) +(expanded ? 370: 0);
        return new LayoutEngine.Size(width, height, List.of(node.title), List.of(node.text), List.of(node.metadata));
    };
    static void run() {
        var nodes = List.of(node("root"), node("left"), node("middle"), node("right"), node("deep"), node("join"),
            node("end"));
        var edges = List.of(edge("root", "left"), edge("root", "middle"), edge("root", "right"), edge("left", "deep"),
            edge("deep", "join"), edge("middle", "join"), edge("right", "join"), edge("join", "end"), new Graph.Edge("end",
            "root", "loop", true));
        Graph graph = new Graph("Synthetic layered graph", nodes, edges, List.of());
        LayoutEngine layout = new LayoutEngine();
        LayoutEngine.Result result = layout.dialogue(graph, Set.of("middle", "deep"), MEASURE);
        assertGeometry(result, "Layered branch/convergence/loop");
        equal(nodes.size(), result.boxes.size(), "Layout retains each visual occurrence once");
        check(result.byId.get("left").x()<result.byId.get("middle").x() && result.byId.get("middle").x()<result.byId.get("right").x(),
            "Player branch source order remains left-to-right");
        for (var edge: edges) if (!edge.back) check(result.byId.get(edge.from).bottom()<result.byId.get(edge.to).y(),
            "Forward edge descends between separate ranks");
        check(result.lines.stream().anyMatch(line -> line.edge().back && line.lane()>0), "Loop routed through a dedicated outer lane");
        assertViewport(result);
        assertConnectors(result);
        var largeNodes = new ArrayList<Graph.Node>();
        var largeEdges = new ArrayList<Graph.Edge>();
        final int count = 20_000;
        for (int i = 0; i<count; i++) {
            largeNodes.add(node("n" + i));
            if (i>0) largeEdges.add(edge("n" +(i - 1), "n" + i));
        }
        var chain = layout.dialogue(new Graph("20,000-node chain", largeNodes, largeEdges, List.of()), Set.of(), MEASURE);
        equal(count, chain.boxes.size(), "Long chain laid out without recursive stack overflow");
        assertGeometry(chain, "20,000-node chain");
        check(chain.visible(0, 0, 2000, 1000).size()<count / 100, "Viewport culling excludes most of a long chain");
        var eventNodes = List.of(event("start", 1), event("left-event", 1), event("right-event", 1), event("early-next",
            2), event("late-current", 1), event("late-next", 2), event("ending", 3));
        var eventEdges = List.of(edge("start", "left-event"), edge("start", "right-event"), edge("left-event", "early-next"),
            edge("right-event", "late-current"), edge("late-current", "late-next"), edge("late-next", "ending"));
        var tree = layout.campaign(new Graph("Deep cross-turn tree", eventNodes, eventEdges, List.of()), Set.of("right-event",
            "late-current"), MEASURE);
        equal(eventNodes.size(), tree.boxes.size(), "Campaign layout retains deep descendants");
        equal(eventEdges.size(), tree.lines.size(), "Campaign connectors survive turn boundaries");
        assertGeometry(tree, "Cross-turn expanded campaign tree");
        assertTurns(tree);
        check(tree.byId.get("early-next").y()> tree.byId.get("late-current").bottom(), "Early branch next-turn event waits for all prior-turn content");
        check(tree.byId.get("left-event").x()<tree.byId.get("right-event").x(), "Campaign sibling order is preserved");
    }
    static void assertGeometry(LayoutEngine.Result result, String label) {
        check(Double.isFinite(result.width) && Double.isFinite(result.height) && result.width>0 && result.height>0,
            label + " has finite positive bounds");
        Set<String> identities = new HashSet<>();
        for (var box: result.boxes) {
            check(identities.add(box.node().id), label + " has unique visual IDs");
            check(Double.isFinite(box.x()) && Double.isFinite(box.y()) && box.w()>0 && box.h()>0, label + " has valid measured rectangles");
            check(box.x() >= 0 && box.y() >= 0 && box.x() + box.w() <= result.width +.001 && box.bottom() <= result.height +.001,
                label + " rectangle stays in fit bounds");
        }
        var sorted = new ArrayList<>(result.boxes);
        sorted.sort(Comparator.comparingDouble(LayoutEngine.Box::y));
        var active = new ArrayList<LayoutEngine.Box>();
        for (var box: sorted) {
            active.removeIf(other -> other.bottom() <= box.y());
            for (var other: active) check(other.x() + other.w() <= box.x() || box.x() + box.w() <= other.x(), label + " rectangles do not overlap: " + box.node().id + " / " + other.node().id);
            active.add(box);
        }
    }
    static void assertConnectors(LayoutEngine.Result result) {
        for (var line: result.lines) for (int i = 1; i<line.points().size(); i++) {
            var a = line.points().get(i - 1);
            var b = line.points().get(i);
            check(a.x() == b.x() || a.y() == b.y(), "Dialogue connector segments are orthogonal");
            for (var box: result.visible(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.abs(a.x() - b.x()),
                Math.abs(a.y() - b.y()))) {
                if (a.x() == b.x()) check(!(a.x()> box.x() +.01 && a.x()<box.x() + box.w() -.01 && Math.max(a.y(),
                    b.y())> box.y() +.01 && Math.min(a.y(), b.y())<box.bottom() -.01), "Vertical connector avoids node interiors");
                else check(!(a.y()> box.y() +.01 && a.y()<box.bottom() -.01 && Math.max(a.x(), b.x())> box.x() +.01 &&
                    Math.min(a.x(), b.x())<box.x() + box.w() -.01), "Horizontal connector avoids node interiors");
            }
        }
    }
    static void assertTurns(LayoutEngine.Result result) {
        for (var box: result.boxes) {
            var matches = result.sectors.stream().filter(sector -> Objects.equals(sector.turn(), box.node().turn)).toList();
            equal(1, matches.size(), "Each campaign node has exactly one matching turn sector");
            var sector = matches.getFirst();
            check(box.y() >= sector.y() && box.bottom() <= sector.y() + sector.height(), "Campaign node stays inside its source turn");
        }
    }
    private static void assertViewport(LayoutEngine.Result result) {
        for (var box: result.boxes) equal(box, result.hit(box.cx(), box.y() + box.h() / 2), "Hit-testing resolves node center");
        double x = 140, y = 100, w = 350, h = 370;
        Set<String> actual = new HashSet<>();
        result.visible(x, y, w, h).forEach(box -> actual.add(box.node().id));
        Set<String> expected = new HashSet<>();
        result.boxes.stream().filter(box -> box.x() + box.w() >= x && box.x() <= x + w && box.bottom() >= y && box.y() <= y + h).forEach(box -> expected.add(box.node().id));
        equal(expected, actual, "Viewport bucket query equals geometric intersection");
        equal(null, result.hit(-100, -100), "Empty canvas does not hit a node");
    }
    private static Graph.Node node(String id) {
        return new Graph.Node(id, Graph.Kind.NARRATOR, id, id + " text", "metadata", "Narrator", "Narrator", null,
            null, null, null);
    }
    private static Graph.Node event(String id, int turn) {
        return new Graph.Node(id, Graph.Kind.EVENT, id, "", "metadata", "Conversation", "", turn, null, null, null);
    }
    private static Graph.Edge edge(String from, String to) {
        return new Graph.Edge(from, to, "", false);
    }
}
