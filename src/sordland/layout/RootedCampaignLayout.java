package sordland.layout;

import sordland.graph.Graph;
import sordland.layout.LayoutEngine.*;
import java.util.*;

public final class RootedCampaignLayout {
    private static final double MARGIN = 92, GAP_X = 42, GAP_Y = 64, PADDING = 14, MEMBER_GAP = 22, MAX_GROUP_WIDTH = 1500;
    private static final Size POINT = new Size(2, 2, List.of(), List.of(), List.of());
    private static final class Unit {
        final String id;
        final List<Graph.Node> nodes = new ArrayList<>();
        final Map<String, Point> offsets = new LinkedHashMap<>();
        Graph.CampaignGroup group;
        boolean news;
        double newsLane;
        final Map<String, Double> annotationLanes = new HashMap<>();
        double width, height, x, y;
        int rank, order;
        Unit(String id) {
            this.id = id;
        }
        double cx() {
            return x + width / 2;
        }
        boolean condition() {
            return group == null && nodes.getFirst().kind == Graph.Kind.CONDITION;
        }
        boolean point() {
            return !news && group == null && nodes.getFirst().kind == Graph.Kind.JUNCTION;
        }
    }
    public Result layout(Graph graph, Set<String> expanded, Measurer measurer) {
        if (graph.campaign == null) throw new IllegalArgumentException("Rooted campaign metadata is required");
        var nodes = new LinkedHashMap<String, Graph.Node>();
        var sizes = new LinkedHashMap<String, Size>();
        for (var node: graph.nodes) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Layout cancelled");
            nodes.put(node.id, node);
            sizes.put(node.id, node.kind == Graph.Kind.JUNCTION ? POINT: measurer.measure(node, expanded.contains(node.id)));
        }
        var units = new LinkedHashMap<String, Unit>();
        var owner = new HashMap<String, Unit>();
        var attachments = new LinkedHashMap<String, List<Graph.NewsAttachment>>();
        for (var attachment: java.util.stream.Stream.concat(graph.campaign.news().stream(), graph.campaign.runtime().stream()).toList()) if (nodes.containsKey(attachment.eventId()) &&
            nodes.containsKey(attachment.effectId())) attachments.computeIfAbsent(attachment.eventId(), k -> new ArrayList<>()).add(attachment);
        for (var group: graph.campaign.groups()) {
            if (group.eventIds().stream().anyMatch(attachments::containsKey)) continue;
            Unit unit = new Unit(group.id());
            unit.group = group;
            packGroup(unit, group, nodes, sizes);
            units.put(unit.id, unit);
            owner.put(group.entryId(), unit);
            owner.put(group.exitId(), unit);
            for (String id: group.eventIds()) owner.put(id, unit);
        }
        for (var entry: attachments.entrySet()) {
            Unit unit = new Unit(entry.getKey());
            unit.news = true;
            packNews(unit, entry.getValue(), nodes, sizes);
            units.put(unit.id, unit);
            for (var node: unit.nodes) owner.put(node.id, unit);
        }
        int order = 0;
        for (var node: graph.nodes) {
            Unit unit = owner.get(node.id);
            if (unit == null) {
                unit = new Unit(node.id);
                unit.nodes.add(node);
                unit.width = sizes.get(node.id).width();
                unit.height = sizes.get(node.id).height();
                units.put(unit.id, unit);
                owner.put(node.id, unit);
            }
            if (unit.order == 0) unit.order =++order;
        }
        order = 0;
        var assigned = new HashSet<Unit>();
        for (var level: graph.campaign.levels()) for (String id: level.eventIds()) if (assigned.add(owner.get(id))) owner.get(id).order = order++;
        var component = new HashMap<String, String>();
        units.keySet().forEach(id -> component.put(id, id));
        for (var level: graph.campaign.levels()) {
            String first = null;
            for (String id: level.eventIds()) {
                Unit unit = owner.get(id);
                if (unit == null) throw new IllegalArgumentException("Absent campaign occurrence: " + id);
                if (first == null) first = unit.id;
                else union(component, first, unit.id);
            }
        }
        var adjacency = new LinkedHashMap<String, Set<String>>();
        var outgoing = new HashMap<Unit, List<Unit>>();
        var indegree = new LinkedHashMap<String, Integer>();
        for (Unit unit: units.values()) indegree.putIfAbsent(find(component, unit.id), 0);
        for (var edge: graph.edges) {
            if (edge.back) throw new IllegalArgumentException("Rooted campaign cannot contain a back edge");
            Unit from = owner.get(edge.from), to = owner.get(edge.to);
            if (from == null || to == null) throw new IllegalArgumentException("Campaign connector has an absent endpoint");
            if (from == to) continue;
            outgoing.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
            String a = find(component, from.id), b = find(component, to.id);
            if (a.equals(b)) throw new IllegalArgumentException("A GameFlow level contains an event-to-event dependency");
            if (adjacency.computeIfAbsent(a, k -> new LinkedHashSet<>()).add(b)) indegree.merge(b, 1, Integer::sum);
        }
        var ready = new ArrayDeque<String>();
        indegree.forEach((id, n) -> {
            if (n == 0) ready.add(id);
        });
        var ranks = new HashMap<String, Integer>();
        int visited = 0;
        while (!ready.isEmpty()) {
            String id = ready.removeFirst();
            visited++;
            for (String next: adjacency.getOrDefault(id, Set.of())) {
                ranks.merge(next, ranks.getOrDefault(id, 0) + 1, Math::max);
                if (indegree.merge(next, -1, Integer::sum) == 0) ready.addLast(next);
            }
        }
        if (visited != indegree.size()) throw new IllegalArgumentException("Rooted campaign contains a cycle");
        var layers = new TreeMap<Integer, List<Unit>>();
        for (Unit unit: units.values()) {
            unit.rank = ranks.getOrDefault(find(component, unit.id), 0);
            layers.computeIfAbsent(unit.rank, k -> new ArrayList<>()).add(unit);
        }
        for (var layer: layers.values()) {
            layer.sort(Comparator.comparingInt(u -> u.order));
            double width = layer.stream().mapToDouble(u -> u.width).sum() + GAP_X *(layer.size() - 1), x = -width / 2;
            for (Unit unit: layer) {
                unit.x = x;
                x += unit.width + GAP_X;
            }
        }
        for (var layer: layers.descendingMap().values()) {
            if (layer.stream().noneMatch(Unit::condition)) continue;
            for (Unit unit: layer) if (unit.condition()) {
                var targets = outgoing.getOrDefault(unit, List.of());
                if (!targets.isEmpty()) unit.x = targets.stream().mapToDouble(Unit::cx).average().orElse(0) - unit.width / 2;
            }
            double right = Double.NEGATIVE_INFINITY;
            for (Unit unit: layer) {
                unit.x = Math.max(unit.x, right + GAP_X);
                right = unit.x + unit.width;
            }
        }
        double minX = units.values().stream().mapToDouble(u -> u.x).min().orElse(0), maxX = units.values().stream().mapToDouble(u -> u.x + u.width).max().orElse(500);
        double shift = MARGIN - minX, width = maxX - minX + 2 * MARGIN;
        var separatorRanks = new LinkedHashMap<Integer, Graph.CampaignTurn>();
        for (var turn: graph.campaign.turns()) {
            Unit entry = owner.get(turn.entryId());
            if (entry != null) separatorRanks.put(entry.rank, turn);
        }
        var sectorStarts = new ArrayList<Sector>();
        var rankTop = new HashMap<Integer, Double>();
        var rankBottom = new HashMap<Integer, Double>();
        double y = MARGIN;
        for (var entry: layers.entrySet()) {
            var turn = separatorRanks.get(entry.getKey());
            if (turn != null) {
                sectorStarts.add(new Sector(turn.turn(), "TURN " + turn.turn() +(turn.title().isBlank() ? "": " · " + turn.title()),
                    y, 0));
                y += 24;
            }
            double height = entry.getValue().stream().mapToDouble(u -> u.height).max().orElse(2);
            rankTop.put(entry.getKey(), y);
            rankBottom.put(entry.getKey(), y + height);
            for (Unit unit: entry.getValue()) {
                unit.x += shift;
                unit.y = y;
            }
            boolean onlyPoints = entry.getValue().stream().allMatch(Unit::point);
            y += height +(onlyPoints ? 42: GAP_Y);
        }
        var sectors = new ArrayList<Sector>();
        for (int i = 0; i<sectorStarts.size(); i++) {
            Sector sector = sectorStarts.get(i);
            double bottom = i + 1<sectorStarts.size() ? sectorStarts.get(i + 1).y(): y;
            sectors.add(new Sector(sector.turn(), sector.label(), sector.y(), bottom - sector.y()));
        }
        var boxesById = new LinkedHashMap<String, Box>();
        var groups = new ArrayList<Group>();
        for (Unit unit: units.values()) {
            if (unit.news) {
                for (var n: unit.nodes) {
                    Point offset = unit.offsets.get(n.id);
                    boxesById.put(n.id, new Box(n, unit.x + offset.x(), unit.y + offset.y(), sizes.get(n.id)));
                }
            } else if (unit.group == null) {
                Graph.Node n = unit.nodes.getFirst();
                boxesById.put(n.id, new Box(n, unit.x, unit.y, sizes.get(n.id)));
            } else {
                var group = unit.group;
                boxesById.put(group.entryId(), new Box(nodes.get(group.entryId()), unit.cx() - 1, unit.y, POINT));
                boxesById.put(group.exitId(), new Box(nodes.get(group.exitId()), unit.cx() - 1, unit.y + unit.height - 2,
                    POINT));
                for (var n: unit.nodes) {
                    Point p = unit.offsets.get(n.id);
                    boxesById.put(n.id, new Box(n, unit.x + p.x(), unit.y + p.y(), sizes.get(n.id)));
                }
                groups.add(new Group(group.id(), unit.x, unit.y, unit.width, unit.height, group.entryId(), group.exitId(),
                    group.eventIds()));
            }
        }
        var boxes = new ArrayList<Box>();
        for (var n: graph.nodes) boxes.add(boxesById.get(n.id));
        var lines = new ArrayList<Line>();
        var parallel = new HashMap<String, List<Graph.Edge>>();
        for (var edge: graph.edges) parallel.computeIfAbsent(edge.from + "\u0000" + edge.to, k -> new ArrayList<>()).add(edge);
        for (var edge: graph.edges) {
            Unit a = owner.get(edge.from), b = owner.get(edge.to);
            Box from = boxesById.get(edge.from), to = boxesById.get(edge.to);
            if (a == b) {
                if (a.news) {
                    double lane = a.x + a.annotationLanes.getOrDefault(from.node().id, a.newsLane), exitY = from.bottom() + 12, enterY = to.y() - 12;
                    var points = List.of(new Point(from.cx(), from.bottom()), new Point(from.cx(), exitY), new Point(lane,
                        exitY), new Point(lane, enterY), new Point(to.cx(), enterY), new Point(to.cx(), to.y()));
                    lines.add(new Line(edge, from, to, lane, clean(points)));
                }
                continue;
            }
            double exitY = rankBottom.get(a.rank) + 14, enterY = rankTop.get(b.rank) - 14;
            if (b.rank == a.rank + 1) exitY = enterY =(rankBottom.get(a.rank) + rankTop.get(b.rank)) / 2;
            var points = List.of(new Point(from.cx(), from.bottom()), new Point(from.cx(), exitY), new Point(to.cx(),
                exitY), new Point(to.cx(), to.y()));
            double lane = 0;
            var alternatives = parallel.get(edge.from + "\u0000" + edge.to);
            boolean distinct = alternatives.size()>1;
            boolean falseRoute = edge.label.toUpperCase(Locale.ROOT).matches(".*\\bFALSE\\b.*");
            if (distinct || falseRoute || crosses(points, units.values(), a, b)) {
                double left = Double.POSITIVE_INFINITY, right = Double.NEGATIVE_INFINITY;
                for (Unit obstacle: units.values()) if (obstacle.rank >= a.rank && obstacle.rank <= b.rank) {
                    left = Math.min(left, obstacle.x - 24);
                    right = Math.max(right, obstacle.x + obstacle.width + 24);
                }
                double leftDistance = Math.abs(from.cx() - left) + Math.abs(to.cx() - left), rightDistance = Math.abs(from.cx() - right) + Math.abs(to.cx() - right);
                int alternative = alternatives.indexOf(edge);
                lane = distinct ?(alternative % 2 == 0 ? left: right):(leftDistance <= rightDistance ? left: right);
                if (distinct) {
                    double track = Math.min(6,(rankTop.get(b.rank) - rankBottom.get(a.rank) - 24) /(alternatives.size() * 2));
                    exitY = rankBottom.get(a.rank) + 12 + alternative * track;
                    enterY = rankTop.get(b.rank) - 12 - alternative * track;
                }
                points = List.of(new Point(from.cx(), from.bottom()), new Point(from.cx(), exitY), new Point(lane,
                    exitY), new Point(lane, enterY), new Point(to.cx(), enterY), new Point(to.cx(), to.y()));
                if (crosses(points, units.values(), a, b)) throw new IllegalArgumentException("Campaign connector cannot clear its cards: " + edge.from + " -> " + edge.to);
            }
            lines.add(new Line(edge, from, to, lane, clean(points)));
        }
        return new Result(graph, boxes, lines, sectors, groups, width, y + MARGIN);
    }
    private static void packNews(Unit unit, List<Graph.NewsAttachment> attachments, Map<String, Graph.Node> nodes,
        Map<String, Size> sizes) {
        Graph.Node host = nodes.get(unit.id);
        unit.nodes.add(host);
        unit.offsets.put(host.id, new Point(0, 0));
        double hostWidth = sizes.get(host.id).width(), startX = hostWidth + 52, y = sizes.get(host.id).height() + 40;
        unit.newsLane = hostWidth + 26;
        double columnWidth = 0;
        for (var attachment: attachments) {
            columnWidth = Math.max(columnWidth, sizes.get(attachment.effectId()).width());
            for (String id: attachment.newsIds()) if (sizes.containsKey(id)) columnWidth = Math.max(columnWidth, sizes.get(id).width());
        }
        int columns = Math.min(3, attachments.size());
        for (int row = 0; row<attachments.size(); row += columns) {
            double rowBottom = y;
            for (int col = 0; col<columns && row + col<attachments.size(); col++) {
                var attachment = attachments.get(row + col);
                Graph.Node effect = nodes.get(attachment.effectId());
                Size effectSize = sizes.get(effect.id);
                double x = startX + col *(columnWidth + 40), nextY = y;
                unit.nodes.add(effect);
                unit.offsets.put(effect.id, new Point(x +(columnWidth - effectSize.width()) / 2, nextY));
                unit.annotationLanes.put(effect.id, x - 18);
                nextY += effectSize.height() + 36;
                for (String id: attachment.newsIds()) if (nodes.containsKey(id)) {
                    Graph.Node news = nodes.get(id);
                    Size size = sizes.get(id);
                    unit.nodes.add(news);
                    unit.offsets.put(id, new Point(x +(columnWidth - size.width()) / 2, nextY));
                    nextY += size.height() + 36;
                }
                rowBottom = Math.max(rowBottom, nextY);
            }
            y = rowBottom + 20;
        }
        unit.width = startX + columns * columnWidth +(columns - 1) * 40;
        unit.height = y - 20;
    }
    private static void packGroup(Unit unit, Graph.CampaignGroup group, Map<String, Graph.Node> nodes, Map<String,
        Size> sizes) {
        var rows = new ArrayList<List<String>>();
        var row = new ArrayList<String>();
        double rowWidth = 0;
        for (String id: group.eventIds()) {
            if (!nodes.containsKey(id)) throw new IllegalArgumentException("Absent sibling group event: " + id);
            double w = sizes.get(id).width();
            if (!row.isEmpty() && rowWidth + MEMBER_GAP + w + 2 * PADDING> MAX_GROUP_WIDTH) {
                rows.add(row);
                row = new ArrayList<>();
                rowWidth = 0;
            }
            if (!row.isEmpty()) rowWidth += MEMBER_GAP;
            row.add(id);
            rowWidth += w;
        }
        if (!row.isEmpty()) rows.add(row);
        unit.width = rows.stream().mapToDouble(r -> r.stream().mapToDouble(id -> sizes.get(id).width()).sum() + MEMBER_GAP *(r.size() - 1)).max().orElse(0) + 2 * PADDING;
        double y = PADDING;
        for (var ids: rows) {
            double w = ids.stream().mapToDouble(id -> sizes.get(id).width()).sum() + MEMBER_GAP *(ids.size() - 1), x =(unit.width - w) / 2, h = 0;
            for (String id: ids) {
                unit.nodes.add(nodes.get(id));
                unit.offsets.put(id, new Point(x, y));
                x += sizes.get(id).width() + MEMBER_GAP;
                h = Math.max(h, sizes.get(id).height());
            }
            y += h + PADDING;
        }
        unit.height = y;
    }
    private static boolean crosses(List<Point> points, Collection<Unit> units, Unit from, Unit to) {
        for (Unit unit: units) {
            if (unit == from || unit == to || unit.point()) continue;
            for (int i = 1; i<points.size(); i++) {
                Point a = points.get(i - 1), b = points.get(i);
                if (a.x() == b.x() && a.x()> unit.x +.01 && a.x()<unit.x + unit.width -.01 && Math.max(a.y(), b.y())> unit.y +.01 &&
                    Math.min(a.y(), b.y())<unit.y + unit.height -.01) return true;
                if (a.y() == b.y() && a.y()> unit.y +.01 && a.y()<unit.y + unit.height -.01 && Math.max(a.x(), b.x())> unit.x +.01 &&
                    Math.min(a.x(), b.x())<unit.x + unit.width -.01) return true;
            }
        }
        return false;
    }
    private static List<Point> clean(List<Point> points) {
        var out = new ArrayList<Point>();
        for (Point point: points) {
            if (!out.isEmpty() && out.getLast().equals(point)) continue;
            while (out.size()>1) {
                Point a = out.get(out.size() - 2), b = out.getLast();
                if (a.x() == b.x() && b.x() == point.x() || a.y() == b.y() && b.y() == point.y()) out.removeLast();
                else break;
            }
            out.add(point);
        }
        return List.copyOf(out);
    }
    private static String find(Map<String, String> parent, String id) {
        String root = id;
        while (!parent.get(root).equals(root)) root = parent.get(root);
        while (!id.equals(root)) {
            String next = parent.get(id);
            parent.put(id, root);
            id = next;
        }
        return root;
    }
    private static void union(Map<String, String> parent, String a, String b) {
        String ar = find(parent, a), br = find(parent, b);
        if (!ar.equals(br)) parent.put(br, ar);
    }
}
