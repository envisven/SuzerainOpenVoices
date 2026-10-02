package sordland.layout;

import sordland.graph.Graph;
import java.util.*;

public final class LayoutEngine {
    public interface Measurer {
        Size measure(Graph.Node node, boolean expanded);
    }
    public record Size(double width, double height, List<String> title, List<String> body, List<String> metadata) {
    }
    public record Box(Graph.Node node, double x, double y, Size size) {
        public double w() {
            return size.width();
        }
        public double h() {
            return size.height();
        }
        public double cx() {
            return x + w() / 2;
        }
        public double bottom() {
            return y + h();
        }
        public boolean contains(double px, double py) {
            return px >= x && px <= x + w() && py >= y && py <= bottom();
        }
    }
    public record Sector(Integer turn, String label, double y, double height) {
    }
    public record Group(String id, double x, double y, double width, double height, String entryId, String exitId,
        List<String> eventIds) {
        public Group {
            eventIds = List.copyOf(eventIds);
        }
        public double cx() {
            return x + width / 2;
        }
        public double bottom() {
            return y + height;
        }
    }
    public record Point(double x, double y) {
    }
    public record Line(Graph.Edge edge, Box from, Box to, double lane, List<Point> points) {
    }
    public static final class Result {
        public final Graph graph;
        public final List<Box> boxes;
        public final List<Line> lines;
        public final List<Sector> sectors;
        public final double width, height;
        public final Map<String, Box> byId;
        public final List<Group> groups;
        private final Map<Integer, List<Box>>buckets = new HashMap<>();
        public Result(Graph graph, List<Box> boxes, List<Line> lines, List<Sector> sectors, double width, double height) {
            this(graph, boxes, lines, sectors, List.of(), width, height);
        }
        public Result(Graph graph, List<Box> boxes, List<Line> lines, List<Sector> sectors, List<Group> groups, double width,
            double height) {
            this.graph = graph;
            this.boxes = List.copyOf(boxes);
            this.lines = List.copyOf(lines);
            this.sectors = List.copyOf(sectors);
            this.groups = List.copyOf(groups);
            this.width = width;
            this.height = height;
            var ids = new LinkedHashMap<String, Box>();
            for (var b: boxes) {
                ids.put(b.node.id, b);
                for (int i =(int) Math.floor(b.y / 400); i <=(int) Math.floor(b.bottom() / 400); i++) buckets.computeIfAbsent(i,
                    k -> new ArrayList<>()).add(b);
            }
            byId = Map.copyOf(ids);
        }
        public List<Box> visible(double x, double y, double w, double h) {
            var result = new LinkedHashSet<Box>();
            int start =(int) Math.floor(y / 400), end =(int) Math.floor((y + h) / 400);
            if (end - start>10000) return boxes.stream().filter(b -> b.x + b.w() >= x && b.x <= x + w && b.bottom() >= y &&
                b.y <= y + h).toList();
            for (int i = start; i <= end; i++) for (var b: buckets.getOrDefault(i, List.of())) if (b.x + b.w() >= x &&
                b.x <= x + w && b.bottom() >= y && b.y <= y + h) result.add(b);
            return List.copyOf(result);
        }
        public Box hit(double x, double y) {
            for (var b: buckets.getOrDefault((int) Math.floor(y / 400), List.of())) if (b.node.kind != Graph.Kind.JUNCTION &&
                b.contains(x, y)) return b;
            return null;
        }
    }
    private static final double GAP_X = 46, GAP_Y = 66, MARGIN = 64;
    public Result campaign(Graph graph, Set<String> expanded, Measurer measurer) {
        if (graph.campaign != null) return new RootedCampaignLayout().layout(graph, expanded, measurer);
        var sizes = measure(graph, expanded, measurer);
        var children = new LinkedHashMap<String, List<String>>();
        var parents = new HashMap<String, String>();
        var byId = new LinkedHashMap<String, Graph.Node>();
        graph.nodes.forEach(n -> byId.put(n.id, n));
        for (var e: graph.edges) if (!e.back && byId.containsKey(e.from) && byId.containsKey(e.to)) {
            if (parents.putIfAbsent(e.to, e.from) != null) throw new IllegalArgumentException("Campaign occurrence has more than one parent: " + e.to);
            children.computeIfAbsent(e.from, k -> new ArrayList<>()).add(e.to);
        }
        var roots = graph.nodes.stream().filter(n -> !parents.containsKey(n.id)).map(n -> n.id).toList();
        var traversal = new ArrayList<String>();
        var groups = new HashMap<String, Integer>();
        var seen = new HashSet<String>();
        for (int i = 0; i<roots.size(); i++) {
            var stack = new ArrayDeque<String>();
            stack.push(roots.get(i));
            while (!stack.isEmpty()) {
                String id = stack.pop();
                if (!seen.add(id)) throw new IllegalArgumentException("Campaign cycle at " + id);
                traversal.add(id);
                groups.put(id, i / 4);
                var cs = children.getOrDefault(id, List.of());
                for (int j = cs.size() - 1; j >= 0; j--) stack.push(cs.get(j));
            }
        }
        if (seen.size() != graph.nodes.size()) throw new IllegalArgumentException("Campaign has a cycle without a root");
        var spans = new HashMap<String, Double>();
        for (int i = traversal.size() - 1; i >= 0; i--) {
            String id = traversal.get(i);
            var cs = children.getOrDefault(id, List.of());
            double total = 0;
            for (String c: cs) total += spans.get(c) + GAP_X;
            if (!cs.isEmpty()) total -= GAP_X;
            spans.put(id, Math.max(sizes.get(id).width(), total));
        }
        var xs = new HashMap<String, Double>();
        double rootX = 116, maxWidth = 1600;
        for (int i = 0; i<roots.size(); i++) {
            if (i % 4 == 0) rootX = 116;
            String root = roots.get(i);
            var stack = new ArrayDeque<Map.Entry<String, Double>>();
            stack.push(Map.entry(root, rootX));
            rootX += spans.get(root) + GAP_X;
            maxWidth = Math.max(maxWidth, rootX + MARGIN);
            while (!stack.isEmpty()) {
                var p = stack.pop();
                String id = p.getKey();
                double left = p.getValue();
                xs.put(id, left +(spans.get(id) - sizes.get(id).width()) / 2);
                double childX = left;
                for (String c: children.getOrDefault(id, List.of())) {
                    stack.push(Map.entry(c, childX));
                    childX += spans.get(c) + GAP_X;
                }
            }
        }
        var levels = new HashMap<String, Integer>();
        var bands = new TreeMap<Integer, TreeMap<Integer, TreeMap<Integer, List<String>>>>();
        for (String id: traversal) {
            var n = byId.get(id);
            String p = parents.get(id);
            int level = p != null && Objects.equals(n.turn, byId.get(p).turn) ? levels.get(p) + 1: 0;
            levels.put(id, level);
            bands.computeIfAbsent(n.turn == null ? Integer.MAX_VALUE: n.turn, k -> new TreeMap<>()).computeIfAbsent(groups.get(id),
                k -> new TreeMap<>()).computeIfAbsent(level, k -> new ArrayList<>()).add(id);
        }
        var boxes = new ArrayList<Box>();
        var sectors = new ArrayList<Sector>();
        double y = MARGIN;
        for (var band: bands.entrySet()) {
            double top = y;
            y += 52;
            for (var group: band.getValue().values()) {
                for (var layer: group.values()) {
                    double height = 0;
                    for (String id: layer) {
                        var size = sizes.get(id);
                        boxes.add(new Box(byId.get(id), xs.get(id), y, size));
                        height = Math.max(height, size.height());
                    }
                    y += height + GAP_Y;
                }
                y += 12;
            }
            y += 10;
            Integer turn = band.getKey() == Integer.MAX_VALUE ? null: band.getKey();
            sectors.add(new Sector(turn, turn == null ? "TURN UNSPECIFIED": "TURN " + turn, top, y - top));
        }
        return finish(graph, boxes, sectors, maxWidth, y + MARGIN);
    }
    public Result dialogue(Graph graph, Set<String> expanded, Measurer measurer) {
        if (!graph.panels.isEmpty()) return new PanelLayout().layout(graph, expanded, measurer);
        var sizes = measure(graph, expanded, measurer);
        var nodes = new LinkedHashMap<String, Graph.Node>();
        graph.nodes.forEach(n -> nodes.put(n.id, n));
        var adj = new LinkedHashMap<String, List<String>>();
        var incoming = new HashMap<String, Integer>();
        nodes.keySet().forEach(id -> incoming.put(id, 0));
        for (var e: graph.edges) if (!e.back && nodes.containsKey(e.from) && nodes.containsKey(e.to)) {
            adj.computeIfAbsent(e.from, k -> new ArrayList<>()).add(e.to);
            incoming.merge(e.to, 1, Integer::sum);
        }
        var queue = new ArrayDeque<String>();
        incoming.forEach((id, count) -> {
            if (count == 0) queue.add(id);
        });
        queue.clear();
        for (String id: nodes.keySet()) if (incoming.get(id) == 0) queue.add(id);
        var rank = new HashMap<String, Integer>();
        var topo = new ArrayList<String>();
        for (String id: queue) rank.put(id, 0);
        while (!queue.isEmpty()) {
            String id = queue.remove();
            topo.add(id);
            for (String c: adj.getOrDefault(id, List.of())) {
                rank.merge(c, rank.getOrDefault(id, 0) + 1, Math::max);
                if (incoming.merge(c, -1, Integer::sum) == 0) queue.add(c);
            }
        }
        if (topo.size() != nodes.size()) throw new IllegalArgumentException("Forward dialogue links contain a cycle that was not marked as a back-reference.");
        var preferred = new LinkedHashMap<String, Integer>();
        for (String root: topo) {
            var stack = new ArrayDeque<String>();
            stack.push(root);
            while (!stack.isEmpty()) {
                String id = stack.pop();
                if (preferred.putIfAbsent(id, preferred.size()) != null) continue;
                var cs = adj.getOrDefault(id, List.of());
                for (int i = cs.size() - 1; i >= 0; i--) stack.push(cs.get(i));
            }
        }
        var orderEdges = new HashMap<String, Set<String>>();
        var orderDegree = new HashMap<String, Integer>();
        nodes.keySet().forEach(id -> orderDegree.put(id, 0));
        int conflictingOrders = 0;
        for (var cs: adj.values()) for (int i = 1; i<cs.size(); i++) {
            String before = cs.get(i - 1), after = cs.get(i);
            if (before.equals(after) || orderEdges.getOrDefault(before, Set.of()).contains(after)) continue;
            if (reaches(orderEdges, after, before)) {
                conflictingOrders++;
                continue;
            }
            orderEdges.computeIfAbsent(before, k -> new LinkedHashSet<>()).add(after);
            orderDegree.merge(after, 1, Integer::sum);
        }
        if (conflictingOrders>0) {
            var notes = new ArrayList<>(graph.diagnostics);
            notes.add(conflictingOrders + " shared-target layout orders conflict. Entries stay canonical; numbered outgoing ports preserve each source's choice order.");
            graph = new Graph(graph.title, graph.nodes, graph.edges, notes);
        }
        var ready = new PriorityQueue<String>(Comparator.comparingInt(preferred::get));
        nodes.keySet().stream().filter(id -> orderDegree.get(id) == 0).forEach(ready::add);
        var horizontalOrder = new ArrayList<String>();
        while (!ready.isEmpty()) {
            String id = ready.remove();
            horizontalOrder.add(id);
            for (String c: orderEdges.getOrDefault(id, Set.of())) if (orderDegree.merge(c, -1, Integer::sum) == 0) ready.add(c);
        }
        if (horizontalOrder.size() != nodes.size()) throw new IllegalArgumentException("Horizontal layout constraints contain an unexpected cycle.");
        var layers = new TreeMap<Integer, List<String>>();
        for (String id: horizontalOrder) layers.computeIfAbsent(rank.get(id), k -> new ArrayList<>()).add(id);
        for (var layer: layers.values()) for (int i = 1; i<layer.size(); i++) orderEdges.computeIfAbsent(layer.get(i - 1),
            k -> new LinkedHashSet<>()).add(layer.get(i));
        double baseCenter = MARGIN + sizes.values().stream().mapToDouble(Size::width).max().orElse(350) / 2;
        var centers = new HashMap<String, Double>();
        for (String id: horizontalOrder) {
            double center = centers.getOrDefault(id, baseCenter);
            centers.put(id, center);
            for (String c: orderEdges.getOrDefault(id, Set.of())) centers.merge(c, center +(sizes.get(id).width() + sizes.get(c).width()) / 2 + GAP_X,
                Math::max);
        }
        for (String id: horizontalOrder) {
            var cs = adj.getOrDefault(id, List.of());
            if (cs.size()>1) {
                double average = cs.stream().mapToDouble(centers::get).average().orElse(baseCenter);
                centers.merge(id, average, Math::max);
            }
        }
        for (String id: horizontalOrder) for (String c: orderEdges.getOrDefault(id, Set.of())) centers.merge(c, centers.get(id) +(sizes.get(id).width() + sizes.get(c).width()) / 2 + GAP_X,
            Math::max);
        var boxes = new ArrayList<Box>();
        double y = MARGIN, maxWidth = 600;
        for (var layer: layers.values()) {
            double height = 0;
            for (String id: layer) {
                var size = sizes.get(id);
                double x = centers.get(id) - size.width() / 2;
                boxes.add(new Box(nodes.get(id), x, y, size));
                height = Math.max(height, size.height());
                maxWidth = Math.max(maxWidth, x + size.width() + MARGIN);
            }
            y += height + GAP_Y;
        }
        return new EdgeRouter().route(graph, boxes, maxWidth + 32);
    }
    private static boolean reaches(Map<String, Set<String>>edges, String start, String target) {
        var visited = new HashSet<String>();
        var queue = new ArrayDeque<String>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String id = queue.remove();
            if (id.equals(target)) return true;
            if (visited.add(id)) queue.addAll(edges.getOrDefault(id, Set.of()));
        }
        return false;
    }
    private Map<String, Size> measure(Graph graph, Set<String> expanded, Measurer m) {
        var out = new HashMap<String, Size>();
        for (var n: graph.nodes) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Layout cancelled");
            out.put(n.id, m.measure(n, expanded.contains(n.id)));
        }
        return out;
    }
    private Result finish(Graph graph, List<Box> boxes, List<Sector> sectors, double width, double height) {
        var ids = new HashMap<String, Box>();
        boxes.forEach(b -> ids.put(b.node.id, b));
        var layerBottoms = new TreeMap<Double, Double>();
        for (var b: boxes) layerBottoms.merge(b.y, b.bottom(), Math::max);
        var layerOrder = new HashMap<Double, Integer>();
        int rank = 0;
        for (double y: layerBottoms.keySet()) layerOrder.put(y, rank++);
        var lines = new ArrayList<Line>();
        record Detour(Graph.Edge edge, Box from, Box to, double startY, double endY) {
        }
        var detours = new ArrayList<Detour>();
        for (var e: graph.edges) {
            var from = ids.get(e.from);
            var to = ids.get(e.to);
            if (from == null || to == null) continue;
            if (!sectors.isEmpty() && !e.back) {
                lines.add(new Line(e, from, to, 0, List.of(new Point(from.cx(), from.bottom()), new Point(to.cx(),
                    to.y))));
                continue;
            }
            double exitY = layerBottoms.get(from.y) + 22, entryY = to.y - 22;
            if (!e.back && layerOrder.get(to.y) == layerOrder.get(from.y) + 1) {
                double channel =(layerBottoms.get(from.y) + to.y) / 2;
                lines.add(new Line(e, from, to, 0, List.of(new Point(from.cx(), from.bottom()), new Point(from.cx(),
                    channel), new Point(to.cx(), channel), new Point(to.cx(), to.y))));
            } else detours.add(new Detour(e, from, to, exitY, entryY));
        }
        detours.sort(Comparator.comparingDouble(d -> Math.min(d.startY, d.endY)));
        var laneEnds = new ArrayList<Double>();
        for (var d: detours) {
            double low = Math.min(d.startY, d.endY), high = Math.max(d.startY, d.endY);
            int lane = 0;
            while (lane<laneEnds.size() && laneEnds.get(lane) + 16 >= low) lane++;
            if (lane == laneEnds.size()) laneEnds.add(high);
            else laneEnds.set(lane, high);
            double x = width + 24 + lane * 14;
            lines.add(new Line(d.edge, d.from, d.to, x, List.of(new Point(d.from.cx(), d.from.bottom()), new Point(d.from.cx(),
                d.startY), new Point(x, d.startY), new Point(x, d.endY), new Point(d.to.cx(), d.endY), new Point(d.to.cx(),
                d.to.y))));
        }
        return new Result(graph, boxes, lines, sectors, width +(laneEnds.isEmpty() ? 0: 48 + laneEnds.size() * 14),
            height);
    }
}
