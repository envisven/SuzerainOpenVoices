package sordland.layout;

import sordland.graph.Graph;
import sordland.data.Domain.EntryKey;
import sordland.graph.Graph.*;
import sordland.layout.LayoutEngine.*;
import java.util.*;

public final class PanelLayout {
    private static final double OUTER_PAD = 38;
    private static final double ENTRY_TO_FIRST_HEADER = 58;
    private static final double HEADER_TO_CHOICES = 42;
    private static final double CONDITION_TO_EFFECT = 24;
    private static final double BRANCH_GAP = 30;
    private static final double CATEGORY_GAP = 64;
    private static final double BOTTOM_PAD = 46;
    private static final double SPINE_INSET = 16;
    private static final double BUDGET_OUTER_PAD_X = 32;
    private static final double BUDGET_GROUP_TOP = 18;
    private static final double BUDGET_GROUP_PAD_TOP = 30;
    private static final double BUDGET_GROUP_PAD_BOTTOM = 26;
    private static final double BUDGET_MINISTRY_GAP = 42;
    private static final double BUDGET_BRANCH_GAP = 20;
    private static final double BUDGET_GROUP_TO_CHOICES = 44;
    private static final double BUDGET_CONDITION_TO_CHOICE = 20;
    private static final double BUDGET_CHOICE_TO_EFFECT = 24;
    private static final double BUDGET_EFFECT_TO_MERGE = 72;
    private static final double BUDGET_BOTTOM_PAD = 54;
    private record BranchPlacement(PanelBranch branch, double centerOffset, double conditionY, double choiceY, double effectY,
        double bottom) {
    }
    private record CategoryPlacement(String headerId, double centerOffset, double headerY, double bottom, List<BranchPlacement> branches) {
        private CategoryPlacement {
            branches = List.copyOf(branches);
        }
    }
    private record Metrics(boolean budget, double width, double height, double groupTop, double groupWidth, double groupHeight,
        double completionY, List<CategoryPlacement> categories) {
        private Metrics {
            categories = List.copyOf(categories);
        }
    }
    public Result layout(Graph graph, Set<String> expanded, Measurer measurer) {
        var byId = new LinkedHashMap<String, Node>();
        var sizes = new HashMap<String, Size>();
        for (Node n: graph.nodes) {
            byId.put(n.id, n);
            sizes.put(n.id, measurer.measure(n, expanded.contains(n.id)));
        }
        if (graph.panels.size() == 1 && isBudget(graph.panels.getFirst(), byId)) return layoutBudgetGateway(graph,
            graph.panels.getFirst(), expanded, measurer, byId, sizes);
        var owners = new HashMap<String, PanelGroup>();
        var metrics = new HashMap<String, Metrics>();
        for (PanelGroup panel: graph.panels) {
            panel.members().forEach(id -> owners.put(id, panel));
            metrics.put(panel.id(), measurePanel(panel, sizes, byId));
        }
        var macroNodes = graph.nodes.stream().filter(n -> !owners.containsKey(n.id) || owners.get(n.id).entryId().equals(n.id)).toList();
        var macroEdges = new ArrayList<Edge>();
        var originals = new IdentityHashMap<Edge, Edge>();
        for (Edge e: graph.edges) {
            PanelGroup from = owners.get(e.from), to = owners.get(e.to);
            if (from != null && from == to) continue;
            String f = from == null ? e.from: from.entryId();
            String t = to == null ? e.to: to.entryId();
            Edge proxy = new Edge(f, t, e.label, e.back);
            macroEdges.add(proxy);
            originals.put(proxy, e);
        }
        Graph macro = new Graph(graph.title, macroNodes, macroEdges, graph.diagnostics);
        Result base = new LayoutEngine().dialogue(macro, expanded,(n, ex) -> {
            PanelGroup panel = owners.get(n.id);
            if (panel == null) return sizes.get(n.id);
            Metrics m = metrics.get(panel.id());
            return new Size(m.width(), m.height(), List.of(), List.of(), List.of());
        });
        var boxes = new ArrayList<Box>();
        var groups = new ArrayList<Group>();
        var groupByPanel = new HashMap<String, Group>();
        for (Box macroBox: base.boxes) {
            PanelGroup panel = owners.get(macroBox.node().id);
            if (panel == null) {
                boxes.add(macroBox);
                continue;
            }
            Metrics m = metrics.get(panel.id());
            double groupX = macroBox.cx() - m.groupWidth() / 2;
            double groupY = macroBox.y() + m.groupTop();
            if (m.budget()) {
                boxes.add(new Box(byId.get(panel.entryId()), macroBox.cx() - 1, groupY, new Size(2, 2, List.of(),
                    List.of(), List.of())));
                for (CategoryPlacement cp: m.categories()) {
                    Box header = at(byId, sizes, cp.headerId(), macroBox.cx() + cp.centerOffset(), macroBox.y() + cp.headerY());
                    boxes.add(header);
                    for (BranchPlacement bp: cp.branches()) {
                        double cx = macroBox.cx() + bp.centerOffset();
                        if (!bp.branch().conditionId().isEmpty()) boxes.add(at(byId, sizes, bp.branch().conditionId(),
                            cx, macroBox.y() + bp.conditionY()));
                        if (!bp.branch().choiceId().isEmpty()) boxes.add(at(byId, sizes, bp.branch().choiceId(), cx,
                            macroBox.y() + bp.choiceY()));
                        boxes.add(at(byId, sizes, bp.branch().effectId(), cx, macroBox.y() + bp.effectY()));
                    }
                }
                boxes.add(at(byId, sizes, panel.completionId(), macroBox.cx(), macroBox.y() + m.completionY()));
                List<String> headerIds = panel.categories().stream().map(PanelCategory::headerId).toList();
                Group group = new Group(panel.id(), groupX, groupY, m.groupWidth(), m.groupHeight(), panel.entryId(),
                    panel.completionId(), headerIds);
                groups.add(group);
                groupByPanel.put(panel.id(), group);
            } else {
                boxes.add(at(byId, sizes, panel.entryId(), macroBox.cx(), macroBox.y()));
                boxes.add(new Box(byId.get(panel.completionId()), macroBox.cx() - 1, macroBox.bottom() - 2, new Size(2,
                    2, List.of(), List.of(), List.of())));
                for (CategoryPlacement cp: m.categories()) {
                    Box header = at(byId, sizes, cp.headerId(), macroBox.cx() + cp.centerOffset(), macroBox.y() + cp.headerY());
                    boxes.add(header);
                    for (BranchPlacement bp: cp.branches()) {
                        double cx = macroBox.cx() + bp.centerOffset();
                        if (!bp.branch().conditionId().isEmpty()) boxes.add(at(byId, sizes, bp.branch().conditionId(),
                            cx, macroBox.y() + bp.conditionY()));
                        if (!bp.branch().choiceId().isEmpty()) boxes.add(at(byId, sizes, bp.branch().choiceId(), cx,
                            macroBox.y() + bp.choiceY()));
                        boxes.add(at(byId, sizes, bp.branch().effectId(), cx, macroBox.y() + bp.effectY()));
                    }
                }
                Group group = new Group(panel.id(), macroBox.x(), macroBox.y(), macroBox.w(), macroBox.h(), panel.entryId(),
                    panel.completionId(), panel.members());
                groups.add(group);
                groupByPanel.put(panel.id(), group);
            }
        }
        var placed = new HashMap<String, Box>();
        boxes.forEach(b -> placed.put(b.node().id, b));
        var lines = new ArrayList<Line>();
        for (Line line: base.lines) {
            Edge original = originals.get(line.edge());
            if (original == null) continue;
            Box from = placed.get(original.from), to = placed.get(original.to);
            if (from == null || to == null) continue;
            PanelGroup fromPanel = owners.get(original.from), toPanel = owners.get(original.to);
            var points = new ArrayList<>(line.points());
            if (fromPanel == null && toPanel != null && !points.isEmpty()) {
                Group group = groupByPanel.get(toPanel.id());
                points.set(points.size() - 1, new Point(group.cx(), group.y()));
            }
            if (fromPanel != null && toPanel == null && !points.isEmpty()) {
                Metrics m = metrics.get(fromPanel.id());
                if (m.budget()) {
                    Box completion = placed.get(fromPanel.completionId());
                    points.set(0, new Point(completion.cx(), completion.bottom()));
                } else {
                    Group group = groupByPanel.get(fromPanel.id());
                    points.set(0, new Point(group.cx(), group.bottom()));
                }
            }
            lines.add(new Line(original, from, to, line.lane(), List.copyOf(points)));
        }
        for (Edge e: graph.edges) {
            PanelGroup panel = owners.get(e.from);
            if (panel == null || panel != owners.get(e.to)) continue;
            Metrics m = metrics.get(panel.id());
            Box from = placed.get(e.from), to = placed.get(e.to);
            if (from == null || to == null) continue;
            if (m.budget()) {
                lines.add(new Line(e, from, to, 0, budgetRoute(panel, e, from, to, placed, groupByPanel.get(panel.id()))));
                continue;
            }
            if (e.from.equals(panel.entryId()) && e.to.equals(panel.completionId())) continue;
            Group group = groupByPanel.get(panel.id());
            List<Point> points;
            if (e.from.equals(panel.entryId())) {
                double railX = group.x() + SPINE_INSET;
                double startY = from.bottom() + 20;
                double entryY = to.y() - 20;
                points = List.of(new Point(from.cx(), from.bottom()), new Point(from.cx(), startY), new Point(railX,
                    startY), new Point(railX, entryY), new Point(to.cx(), entryY), new Point(to.cx(), to.y()));
            } else if (isHeader(panel, e.from)) {
                double fanY = from.bottom() + 18;
                double entryY = to.y() - 16;
                points = List.of(new Point(from.cx(), from.bottom()), new Point(from.cx(), fanY), new Point(to.cx(),
                    fanY), new Point(to.cx(), entryY), new Point(to.cx(), to.y()));
            } else {
                double mid =(from.bottom() + to.y()) / 2;
                points = List.of(new Point(from.cx(), from.bottom()), new Point(from.cx(), mid), new Point(to.cx(),
                    mid), new Point(to.cx(), to.y()));
            }
            lines.add(new Line(e, from, to, 0, points));
        }
        return new Result(graph, boxes, lines, base.sectors, groups, base.width, base.height);
    }
    private static List<Point> budgetRoute(PanelGroup panel, Edge edge, Box from, Box to, Map<String, Box> placed,
        Group group) {
        if (edge.from.equals(panel.entryId()) && isHeader(panel, edge.to)) {
            double busY = group.y() + 18;
            return List.of(new Point(group.cx(), group.y()), new Point(group.cx(), busY), new Point(to.cx(), busY),
                new Point(to.cx(), to.y()));
        }
        if (isHeader(panel, edge.from)) {
            double fanY = from.bottom() + 20;
            return List.of(new Point(from.cx(), from.bottom()), new Point(from.cx(), fanY), new Point(to.cx(), fanY),
                new Point(to.cx(), to.y()));
        }
        if (edge.to.equals(panel.completionId())) {
            double mergeY = to.y() - 30;
            return List.of(new Point(from.cx(), from.bottom()), new Point(from.cx(), mergeY), new Point(to.cx(), mergeY),
                new Point(to.cx(), to.y()));
        }
        if (Math.abs(from.cx() - to.cx())<0.01) {
            return List.of(new Point(from.cx(), from.bottom()), new Point(to.cx(), to.y()));
        }
        double mid =(from.bottom() + to.y()) / 2;
        return List.of(new Point(from.cx(), from.bottom()), new Point(from.cx(), mid), new Point(to.cx(), mid), new Point(to.cx(),
            to.y()));
    }
    private static Result layoutBudgetGateway(Graph graph, PanelGroup panel, Set<String> expanded, Measurer measurer,
        Map<String, Node> nodes, Map<String, Size> sizes) {
        Set<String> members = new LinkedHashSet<>(panel.members());
        var forward = new HashMap<String, List<String>>();
        var reverse = new HashMap<String, List<String>>();
        for (Edge edge: graph.edges) {
            if (members.contains(edge.from) && members.contains(edge.to)) continue;
            forward.computeIfAbsent(edge.from, k -> new ArrayList<>()).add(edge.to);
            reverse.computeIfAbsent(edge.to, k -> new ArrayList<>()).add(edge.from);
        }
        Set<String> pre = reachable(panel.entryId(), reverse);
        Set<String> post = reachable(panel.completionId(), forward);
        pre.removeAll(members);
        post.removeAll(members);
        var overlap = new LinkedHashSet<>(pre);
        overlap.retainAll(post);
        if (!overlap.isEmpty()) throw new IllegalArgumentException("Panel_Budget is no longer a source gateway; pre/post overlap: " + overlap);
        EntryKey panelSource = nodes.get(panel.entryId()).source;
        for (Node node: graph.nodes) {
            if (members.contains(node.id) || pre.contains(node.id) || post.contains(node.id)) continue;
            if (panelSource != null && node.source != null && node.source.conversationId() == panelSource.conversationId()) pre.add(node.id);
            else pre.add(node.id);
        }
        var bypass = new ArrayList<Edge>();
        for (Edge edge: graph.edges) if (pre.contains(edge.from) && post.contains(edge.to)) bypass.add(edge);
        if (!bypass.isEmpty()) throw new IllegalArgumentException("Source progression bypasses Panel_Budget: " + bypass.stream().map(e -> e.from + " -> " + e.to).toList());
        Graph preGraph = subgraph(graph, pre);
        Graph postGraph = subgraph(graph, post);
        Result preLayout = preGraph.nodes.isEmpty() ? empty(graph): new LayoutEngine().dialogue(preGraph, intersect(expanded,
            pre), measurer);
        Result postLayout = postGraph.nodes.isEmpty() ? empty(graph): new LayoutEngine().dialogue(postGraph, intersect(expanded,
            post), measurer);
        Metrics metrics = measureBudget(panel, sizes, nodes);
        double preMaxBottom = preLayout.boxes.stream().mapToDouble(Box::bottom).max().orElse(64);
        double panelMacroY = preMaxBottom + 82 - metrics.groupTop();
        double fullGroupX = 0;
        double fullGroupY = panelMacroY + metrics.groupTop();
        Size completionSize = sizes.get(panel.completionId());
        double fullGroupHeight = metrics.completionY() + completionSize.height() + BUDGET_GROUP_PAD_BOTTOM - metrics.groupTop();
        double fullPanelBottom = fullGroupY + fullGroupHeight;
        double overallWidth = Math.max(metrics.width(), Math.max(preLayout.width, postLayout.width));
        overallWidth = Math.max(overallWidth, 900) + 128;
        double center = overallWidth / 2;
        double preDx =(overallWidth - preLayout.width) / 2;
        double postDx =(overallWidth - postLayout.width) / 2;
        double postMinY = postLayout.boxes.stream().mapToDouble(Box::y).min().orElse(64);
        double postDy = fullPanelBottom + 96 - postMinY;
        fullGroupX = center - metrics.groupWidth() / 2;
        var boxes = new ArrayList<Box>();
        var lines = new ArrayList<Line>();
        appendShifted(preLayout, preDx, 0, boxes, lines);
        appendShifted(postLayout, postDx, postDy, boxes, lines);
        boxes.add(new Box(nodes.get(panel.entryId()), center - 1, fullGroupY, new Size(2, 2, List.of(), List.of(),
            List.of())));
        for (CategoryPlacement cp: metrics.categories()) {
            boxes.add(at(nodes, sizes, cp.headerId(), center + cp.centerOffset(), panelMacroY + cp.headerY()));
            for (BranchPlacement bp: cp.branches()) {
                double cx = center + bp.centerOffset();
                if (!bp.branch().conditionId().isEmpty()) boxes.add(at(nodes, sizes, bp.branch().conditionId(), cx,
                    panelMacroY + bp.conditionY()));
                if (!bp.branch().choiceId().isEmpty()) boxes.add(at(nodes, sizes, bp.branch().choiceId(), cx, panelMacroY + bp.choiceY()));
                boxes.add(at(nodes, sizes, bp.branch().effectId(), cx, panelMacroY + bp.effectY()));
            }
        }
        boxes.add(at(nodes, sizes, panel.completionId(), center, panelMacroY + metrics.completionY()));
        Group group = new Group(panel.id(), fullGroupX, fullGroupY, metrics.groupWidth(), fullGroupHeight, panel.entryId(),
            panel.completionId(), panel.members());
        var groups = List.of(group);
        var placed = new HashMap<String, Box>();
        boxes.forEach(b -> placed.put(b.node().id, b));
        for (Edge edge: graph.edges) {
            if (!members.contains(edge.from) || !members.contains(edge.to)) continue;
            Box from = placed.get(edge.from), to = placed.get(edge.to);
            if (from == null || to == null) continue;
            lines.add(new Line(edge, from, to, 0, budgetRoute(panel, edge, from, to, placed, group)));
        }
        double preLeft = boxes.stream().filter(b -> pre.contains(b.node().id)).mapToDouble(Box::x).min().orElse(group.x());
        double incomingLane = Math.min(preLeft - 36, group.x() - 36);
        for (Edge edge: graph.edges) {
            if (!edge.to.equals(panel.entryId()) || members.contains(edge.from)) continue;
            Box from = placed.get(edge.from), to = placed.get(panel.entryId());
            if (from == null || to == null) continue;
            double exitY = from.bottom() + 24;
            double mergeY = group.y() - 28;
            lines.add(new Line(edge, from, to, incomingLane, List.of(new Point(from.cx(), from.bottom()), new Point(from.cx(),
                exitY), new Point(incomingLane, exitY), new Point(incomingLane, mergeY), new Point(group.cx(), mergeY),
                new Point(group.cx(), group.y()))));
        }
        for (Edge edge: graph.edges) {
            if (!edge.from.equals(panel.completionId()) || members.contains(edge.to)) continue;
            Box from = placed.get(panel.completionId()), to = placed.get(edge.to);
            if (from == null || to == null) continue;
            double fanY = Math.min(to.y() - 28, group.bottom() + 34);
            if (fanY<group.bottom() + 18) fanY = group.bottom() + 18;
            lines.add(new Line(edge, from, to, 0, List.of(new Point(from.cx(), from.bottom()), new Point(group.cx(),
                group.bottom()), new Point(group.cx(), fanY), new Point(to.cx(), fanY), new Point(to.cx(), to.y()))));
        }
        for (Edge edge: graph.edges) {
            if (members.contains(edge.from) || members.contains(edge.to)) continue;
            boolean samePre = pre.contains(edge.from) && pre.contains(edge.to);
            boolean samePost = post.contains(edge.from) && post.contains(edge.to);
            if (samePre || samePost) continue;
            Box from = placed.get(edge.from), to = placed.get(edge.to);
            if (from == null || to == null) continue;
            double lane = group.x() - 48;
            lines.add(new Line(edge, from, to, lane, List.of(new Point(from.cx(), from.bottom()), new Point(lane,
                from.bottom()), new Point(lane, to.y()), new Point(to.cx(), to.y()))));
        }
        double maxBottom = boxes.stream().mapToDouble(Box::bottom).max().orElse(fullPanelBottom);
        double maxRight = boxes.stream().mapToDouble(b -> b.x() + b.w()).max().orElse(overallWidth - 64);
        double width = Math.max(overallWidth, maxRight + 64);
        double height = maxBottom + 64;
        return new Result(graph, boxes, lines, List.of(), groups, width, height);
    }
    private static Set<String> reachable(String start, Map<String, List<String>>edges) {
        var out = new LinkedHashSet<String>();
        var queue = new ArrayDeque<String>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            if (!out.add(id)) continue;
            queue.addAll(edges.getOrDefault(id, List.of()));
        }
        return out;
    }
    private static Set<String> intersect(Set<String> values, Set<String> allowed) {
        var result = new LinkedHashSet<String>();
        for (String value: values) if (allowed.contains(value)) result.add(value);
        return result;
    }
    private static Graph subgraph(Graph source, Set<String> ids) {
        var nodes = source.nodes.stream().filter(n -> ids.contains(n.id)).toList();
        var edges = source.edges.stream().filter(e -> ids.contains(e.from) && ids.contains(e.to)).toList();
        return new Graph(source.title, nodes, edges, source.diagnostics);
    }
    private static Result empty(Graph source) {
        return new Result(new Graph(source.title, List.of(), List.of(), source.diagnostics), List.of(), List.of(),
            List.of(), 128, 128);
    }
    private static void appendShifted(Result source, double dx, double dy, List<Box> boxes, List<Line> lines) {
        var shifted = new HashMap<String, Box>();
        for (Box box: source.boxes) {
            Box moved = new Box(box.node(), box.x() + dx, box.y() + dy, box.size());
            boxes.add(moved);
            shifted.put(moved.node().id, moved);
        }
        for (Line line: source.lines) {
            Box from = shifted.get(line.edge().from), to = shifted.get(line.edge().to);
            if (from == null || to == null) continue;
            var points = line.points().stream().map(p -> new Point(p.x() + dx, p.y() + dy)).toList();
            lines.add(new Line(line.edge(), from, to, line.lane() == 0 ? 0: line.lane() + dx, points));
        }
    }
    private static Metrics measurePanel(PanelGroup panel, Map<String, Size> sizes, Map<String, Node> nodes) {
        return isBudget(panel, nodes) ? measureBudget(panel, sizes, nodes): measureGeneric(panel, sizes, nodes);
    }
    private static Metrics measureBudget(PanelGroup panel, Map<String, Size> sizes, Map<String, Node> nodes) {
        var orderedCategories = new ArrayList<>(panel.categories());
        var categoryWidths = new LinkedHashMap<String, Double>();
        double maxHeaderHeight = 0, maxConditionHeight = 0, maxChoiceHeight = 0, maxEffectHeight = 0;
        for (PanelCategory category: orderedCategories) {
            Size header = sizes.get(category.headerId());
            maxHeaderHeight = Math.max(maxHeaderHeight, header.height());
            var branches = new ArrayList<>(category.branches());
            branches.sort(Comparator.comparingInt(b -> budgetVisualOrder(branchNode(b, nodes))));
            double rowWidth = 0;
            for (int i = 0; i<branches.size(); i++) {
                PanelBranch branch = branches.get(i);
                double cell = sizes.get(branch.effectId()).width();
                if (!branch.choiceId().isEmpty()) {
                    cell = Math.max(cell, sizes.get(branch.choiceId()).width());
                    maxChoiceHeight = Math.max(maxChoiceHeight, sizes.get(branch.choiceId()).height());
                }
                if (!branch.conditionId().isEmpty()) {
                    cell = Math.max(cell, sizes.get(branch.conditionId()).width());
                    maxConditionHeight = Math.max(maxConditionHeight, sizes.get(branch.conditionId()).height());
                }
                maxEffectHeight = Math.max(maxEffectHeight, sizes.get(branch.effectId()).height());
                rowWidth += cell;
                if (i>0) rowWidth += BUDGET_BRANCH_GAP;
            }
            categoryWidths.put(category.headerId(), Math.max(header.width(), rowWidth));
        }
        double contentWidth = categoryWidths.values().stream().mapToDouble(Double::doubleValue).sum();
        if (categoryWidths.size()>1) contentWidth += BUDGET_MINISTRY_GAP *(categoryWidths.size() - 1);
        double width = contentWidth + BUDGET_OUTER_PAD_X * 2;
        double groupTop = BUDGET_GROUP_TOP;
        double groupHeight = BUDGET_GROUP_PAD_TOP + maxHeaderHeight + BUDGET_GROUP_PAD_BOTTOM;
        double headerY = groupTop + BUDGET_GROUP_PAD_TOP;
        double choiceBandTop = groupTop + groupHeight + BUDGET_GROUP_TO_CHOICES;
        double conditionY = maxConditionHeight>0 ? choiceBandTop: -1;
        double choiceY = choiceBandTop +(maxConditionHeight>0 ? maxConditionHeight + BUDGET_CONDITION_TO_CHOICE: 0);
        double effectY = choiceY + maxChoiceHeight + BUDGET_CHOICE_TO_EFFECT;
        var placements = new ArrayList<CategoryPlacement>();
        double x = -contentWidth / 2;
        for (PanelCategory category: orderedCategories) {
            double categoryWidth = categoryWidths.get(category.headerId());
            double categoryCenter = x + categoryWidth / 2;
            var branches = new ArrayList<>(category.branches());
            branches.sort(Comparator.comparingInt(b -> budgetVisualOrder(branchNode(b, nodes))));
            var cells = new ArrayList<Double>();
            for (PanelBranch branch: branches) {
                double cell = sizes.get(branch.effectId()).width();
                if (!branch.choiceId().isEmpty()) cell = Math.max(cell, sizes.get(branch.choiceId()).width());
                if (!branch.conditionId().isEmpty()) cell = Math.max(cell, sizes.get(branch.conditionId()).width());
                cells.add(cell);
            }
            double rowWidth = cells.stream().mapToDouble(Double::doubleValue).sum();
            if (cells.size()>1) rowWidth += BUDGET_BRANCH_GAP *(cells.size() - 1);
            double bx = categoryCenter - rowWidth / 2;
            var branchPlacements = new ArrayList<BranchPlacement>();
            for (int i = 0; i<branches.size(); i++) {
                PanelBranch branch = branches.get(i);
                double cell = cells.get(i), center = bx + cell / 2;
                double cy = branch.conditionId().isEmpty() ? -1: conditionY;
                double bottom = effectY + sizes.get(branch.effectId()).height();
                branchPlacements.add(new BranchPlacement(branch, center, cy, choiceY, effectY, bottom));
                bx += cell + BUDGET_BRANCH_GAP;
            }
            placements.add(new CategoryPlacement(category.headerId(), categoryCenter, headerY, effectY + maxEffectHeight,
                branchPlacements));
            x += categoryWidth + BUDGET_MINISTRY_GAP;
        }
        Size completion = sizes.get(panel.completionId());
        double completionY = effectY + maxEffectHeight + BUDGET_EFFECT_TO_MERGE;
        double height = completionY + completion.height() + BUDGET_BOTTOM_PAD;
        return new Metrics(true, width, height, groupTop, width, groupHeight, completionY, placements);
    }
    private static Metrics measureGeneric(PanelGroup panel, Map<String, Size> sizes, Map<String, Node> nodes) {
        Size entry = sizes.get(panel.entryId());
        double cursor = entry.height() + ENTRY_TO_FIRST_HEADER;
        double widest = entry.width();
        var categories = new ArrayList<CategoryPlacement>();
        for (PanelCategory category: panel.categories()) {
            Size header = sizes.get(category.headerId());
            double headerY = cursor;
            double branchTop = headerY + header.height() + HEADER_TO_CHOICES;
            var ordered = new ArrayList<>(category.branches());
            var cellWidths = new ArrayList<Double>();
            for (PanelBranch branch: ordered) {
                double w = sizes.get(branch.effectId()).width();
                if (!branch.choiceId().isEmpty()) w = Math.max(w, sizes.get(branch.choiceId()).width());
                if (!branch.conditionId().isEmpty()) w = Math.max(w, sizes.get(branch.conditionId()).width());
                cellWidths.add(w);
            }
            double rowWidth = cellWidths.stream().mapToDouble(Double::doubleValue).sum();
            if (cellWidths.size()>1) rowWidth += BRANCH_GAP *(cellWidths.size() - 1);
            widest = Math.max(widest, Math.max(header.width(), rowWidth));
            double left = -rowWidth / 2;
            double categoryBottom = branchTop;
            var placements = new ArrayList<BranchPlacement>();
            for (int i = 0; i<ordered.size(); i++) {
                PanelBranch branch = ordered.get(i);
                double cell = cellWidths.get(i), centerOffset = left + cell / 2;
                double conditionY = -1, choiceY = -1, effectY = branchTop;
                if (!branch.conditionId().isEmpty()) {
                    conditionY = branchTop;
                    effectY = conditionY + sizes.get(branch.conditionId()).height() + CONDITION_TO_EFFECT;
                }
                if (!branch.choiceId().isEmpty()) {
                    choiceY = effectY;
                    effectY = choiceY + sizes.get(branch.choiceId()).height() + CONDITION_TO_EFFECT;
                }
                double bottom = effectY + sizes.get(branch.effectId()).height();
                categoryBottom = Math.max(categoryBottom, bottom);
                placements.add(new BranchPlacement(branch, centerOffset, conditionY, choiceY, effectY, bottom));
                left += cell + BRANCH_GAP;
            }
            if (ordered.isEmpty()) categoryBottom = headerY + header.height();
            categories.add(new CategoryPlacement(category.headerId(), 0, headerY, categoryBottom, placements));
            cursor = categoryBottom + CATEGORY_GAP;
        }
        if (!categories.isEmpty()) cursor -= CATEGORY_GAP;
        double width = widest + OUTER_PAD * 2;
        double height = cursor + BOTTOM_PAD;
        return new Metrics(false, width, height, 0, width, height, height - 2, categories);
    }
    private static Node branchNode(PanelBranch branch, Map<String, Node> nodes) {
        if (!branch.choiceId().isEmpty()) return nodes.get(branch.choiceId());
        return nodes.get(branch.effectId());
    }
    private static boolean isBudget(PanelGroup panel, Map<String, Node> nodes) {
        Node entry = nodes.get(panel.entryId());
        return entry != null && entry.item != null && "Panel_Budget".equals(entry.item.internalName());
    }
    private static int budgetVisualOrder(Node node) {
        if (node == null) return 99;
        String t = node.title.toUpperCase(Locale.ROOT);
        if (t.startsWith("DECREASE")) return 0;
        if (t.startsWith("MAINTAIN")) return 1;
        if (t.startsWith("INCREASE")) return 2;
        return 99;
    }
    private static boolean isHeader(PanelGroup panel, String id) {
        return panel.categories().stream().anyMatch(c -> c.headerId().equals(id));
    }
    private static Box at(Map<String, Node> nodes, Map<String, Size> sizes, String id, double cx, double y) {
        Size size = sizes.get(id);
        return new Box(nodes.get(id), cx - size.width() / 2, y, size);
    }
}
