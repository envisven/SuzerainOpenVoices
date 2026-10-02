package sordland.layout;

import sordland.graph.Graph;
import sordland.layout.LayoutEngine.*;
import java.util.*;

public final class EdgeRouter {
    private static final double TRACK = 14, PORT = 10, MARGIN = 64;
    private record Port(double x, Graph.Edge edge) {
    }
    private static final class Track {
        final int gap;
        final double left, right;
        int slot;
        Track(int gap, double x, double y) {
            this.gap = gap;
            left = Math.min(x, y);
            right = Math.max(x, y);
        }
    }
    private static final class Route {
        final Graph.Edge edge;
        final Box from, to;
        final int start, end;
        double sx, tx, lane;
        Track exit, enter;
        Route(Graph.Edge edge, Box from, Box to, int start, int end) {
            this.edge = edge;
            this.from = from;
            this.to = to;
            this.start = start;
            this.end = end;
        }
        boolean adjacent() {
            return !edge.back && end == start + 1;
        }
    }
    public Result route(Graph graph, List<Box> original, double width) {
        if (original.isEmpty()) return new Result(graph, List.of(), List.of(), List.of(), width, 128);
        var byId = new LinkedHashMap<String, Box>();
        var layers = new TreeMap<Double, List<Box>>();
        for (var box: original) {
            byId.put(box.node().id, box);
            layers.computeIfAbsent(box.y(), k -> new ArrayList<>()).add(box);
        }
        var ranks = new HashMap<String, Integer>();
        int rank = 0;
        for (var layer: layers.values()) {
            for (var box: layer) ranks.put(box.node().id, rank);
            rank++;
        }
        var routes = new ArrayList<Route>();
        var outgoing = new LinkedHashMap<String, List<Route>>();
        var incoming = new LinkedHashMap<String, List<Route>>();
        for (var edge: graph.edges) {
            Box from = byId.get(edge.from), to = byId.get(edge.to);
            if (from == null || to == null) throw new IllegalArgumentException("Connector has an absent endpoint");
            Route route = new Route(edge, from, to, ranks.get(edge.from), ranks.get(edge.to));
            routes.add(route);
            outgoing.computeIfAbsent(edge.from, k -> new ArrayList<>()).add(route);
            incoming.computeIfAbsent(edge.to, k -> new ArrayList<>()).add(route);
        }
        var occupied = new HashMap<Integer, List<Port>>();
        for (var group: outgoing.values()) {
            Box box = group.getFirst().from;
            double spread = Math.min(box.w() - 44, Math.max(0,(group.size() - 1) * 22));
            for (int i = 0; i<group.size(); i++) {
                var r = group.get(i);
                r.sx = box.cx() +(group.size() == 1 ? 0:(double) i /(group.size() - 1) * spread - spread / 2);
                occupied.computeIfAbsent(r.start, k -> new ArrayList<>()).add(new Port(r.sx, r.edge));
            }
        }
        for (var group: incoming.values()) {
            group.sort(Comparator.comparingDouble(r -> r.from.cx()));
            Box box = group.getFirst().to;
            double spread = Math.min(box.w() - 44, Math.max(0,(group.size() - 1) * 22));
            for (int i = 0; i<group.size(); i++) {
                var r = group.get(i);
                double preferred = box.cx() +(group.size() == 1 ? 0:(double) i /(group.size() - 1) * spread - spread / 2);
                var ports = occupied.computeIfAbsent(r.end - 1, k -> new ArrayList<>());
                r.tx = freePort(preferred, box.x() + 18, box.x() + box.w() - 18, ports, r.edge);
                ports.add(new Port(r.tx, r.edge));
            }
        }
        var detours = routes.stream().filter(r -> !r.adjacent()).sorted(Comparator.comparingInt(r -> Math.min(r.start,
            r.end))).toList();
        var laneEnds = new ArrayList<Integer>();
        for (var r: detours) {
            int low = Math.min(r.start, r.end) - 1, high = Math.max(r.start, r.end) + 1, lane = 0;
            while (lane<laneEnds.size() && laneEnds.get(lane) >= low) lane++;
            if (lane == laneEnds.size()) laneEnds.add(high);
            else laneEnds.set(lane, high);
            r.lane = width + 24 + lane * 18;
        }
        var tracks = new TreeMap<Integer, List<Track>>();
        for (var r: routes) {
            if (r.adjacent()) r.exit = new Track(r.start, r.sx, r.tx);
            else {
                r.exit = new Track(r.start, r.sx, r.lane);
                r.enter = new Track(r.end - 1, r.tx, r.lane);
                tracks.computeIfAbsent(r.enter.gap, k -> new ArrayList<>()).add(r.enter);
            }
            tracks.computeIfAbsent(r.exit.gap, k -> new ArrayList<>()).add(r.exit);
        }
        var gapHeights = new HashMap<Integer, Double>();
        for (var entry: tracks.entrySet()) {
            var ordered = entry.getValue();
            ordered.sort(Comparator.comparingDouble(t -> t.left));
            var rightEnds = new ArrayList<Double>();
            for (var track: ordered) {
                int slot = 0;
                while (slot<rightEnds.size() && rightEnds.get(slot) + PORT >= track.left) slot++;
                if (slot == rightEnds.size()) rightEnds.add(track.right);
                else rightEnds.set(slot, track.right);
                track.slot = slot;
            }
            gapHeights.put(entry.getKey(), Math.max(66, 40 + rightEnds.size() * TRACK));
        }
        var gapTops = new HashMap<Integer, Double>();
        gapTops.put(-1, MARGIN);
        double y = MARGIN + gapHeights.getOrDefault(-1, 0d);
        var boxes = new ArrayList<Box>();
        rank = 0;
        for (var layer: layers.values()) {
            double height = 0;
            for (var b: layer) {
                boxes.add(new Box(b.node(), b.x(), y, b.size()));
                height = Math.max(height, b.h());
            }
            gapTops.put(rank, y + height);
            y += height + gapHeights.getOrDefault(rank, 66d);
            rank++;
        }
        byId.clear();
        for (var box: boxes) byId.put(box.node().id, box);
        var lines = new ArrayList<Line>();
        for (var r: routes) {
            Box from = byId.get(r.edge.from), to = byId.get(r.edge.to);
            double exitY = gapTops.get(r.exit.gap) + 20 + r.exit.slot * TRACK;
            var points = new ArrayList<Point>();
            points.add(new Point(r.sx, from.bottom()));
            points.add(new Point(r.sx, exitY));
            if (r.adjacent()) points.add(new Point(r.tx, exitY));
            else {
                double enterY = gapTops.get(r.enter.gap) + 20 + r.enter.slot * TRACK;
                points.add(new Point(r.lane, exitY));
                points.add(new Point(r.lane, enterY));
                points.add(new Point(r.tx, enterY));
            }
            points.add(new Point(r.tx, to.y()));
            lines.add(new Line(r.edge, from, to, r.lane, clean(points)));
        }
        return new Result(graph, boxes, lines, List.of(), width +(laneEnds.isEmpty() ? 0: 60 + laneEnds.size() * 18),
            y + MARGIN);
    }
    private static double freePort(double preferred, double left, double right, List<Port> occupied, Graph.Edge edge) {
        for (double spacing: new double[] {
            PORT, 5, 2,.5
        }) {
            for (int step = 0; step <= Math.ceil((right - left) / spacing) + 1; step++) for (int direction: new int[] {
                1, -1
            }) {
                double candidate = preferred + step * spacing * direction;
                if (candidate<left || candidate> right) continue;
                boolean free = true;
                for (var port: occupied) if (port.edge != edge && Math.abs(port.x - candidate)<spacing -.0001) {
                    free = false;
                    break;
                }
                if (free) return candidate;
            }
        }
        throw new IllegalArgumentException("Too many incoming connectors for distinct ports on one node");
    }
    private static List<Point> clean(List<Point> points) {
        var result = new ArrayList<Point>();
        for (var point: points) {
            if (!result.isEmpty() && result.getLast().equals(point)) continue;
            while (result.size() >= 2) {
                var a = result.get(result.size() - 2);
                var b = result.getLast();
                if (a.x() == b.x() && b.x() == point.x() || a.y() == b.y() && b.y() == point.y()) result.removeLast();
                else break;
            }
            result.add(point);
        }
        return List.copyOf(result);
    }
}
