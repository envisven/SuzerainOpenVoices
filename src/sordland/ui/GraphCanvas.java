package sordland.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import sordland.graph.Graph;
import sordland.layout.EdgeHitTest;
import sordland.layout.LayoutEngine.*;
import java.util.*;
import java.util.function.*;

public final class GraphCanvas extends Region {
    private final Canvas canvas = new Canvas();
    private Result result;
    private final Viewport viewport = new Viewport();
    private double pressX, pressY, lastX, lastY;
    private boolean dragged;
    private String selected;
    private boolean speakerColors;
    private String campaignQuery = "";
    private final TextMeasurer textMetrics = new TextMeasurer();
    private final Tooltip turnTooltip = new Tooltip();
    private final EdgeSelection edgeSelection = new EdgeSelection();
    private Map<String, Color> speakerPalette = Map.of();
    public void setSpeakerPalette(Map<String, Color> palette) {
        speakerPalette = Map.copyOf(palette);
    }
    public BiConsumer<Graph.Node, Boolean> onNode =(n, metadata) -> {
    };
    public Consumer<Graph.Edge> onEdge = e -> {
    };
    public Consumer<String> onStatus = s -> {
    };
    public GraphCanvas() {
        getChildren().add(canvas);
        setMinSize(100, 100);
        setStyle("-fx-background-color: " + Theme.BACKGROUND + ";");
        Tooltip.install(canvas, turnTooltip);
        canvas.setOnScroll(e -> {
            zoomAt(Math.pow(1.0018, e.getDeltaY()), e.getX(), e.getY());
            e.consume();
        });
        canvas.setOnMousePressed(e -> {
            pressX = lastX = e.getX();
            pressY = lastY = e.getY();
            dragged = false;
            canvas.requestFocus();
        });
        canvas.setOnMouseDragged(e -> {
            if (Math.hypot(e.getX() - pressX, e.getY() - pressY)>4) dragged = true;
            if (dragged) {
                viewport.panX += e.getX() - lastX;
                viewport.panY += e.getY() - lastY;
                redraw();
            }
            lastX = e.getX();
            lastY = e.getY();
        });
        canvas.setOnMouseReleased(e -> {
            if (dragged || result == null || e.getButton() != MouseButton.PRIMARY) return;
            if (!result.sectors.isEmpty() && e.getX()<64) return;
            double x =(e.getX() - viewport.panX) / viewport.scale, y =(e.getY() - viewport.panY) / viewport.scale;
            Box b = result.hit(x, y);
            if (b != null) {
                selected = b.node().id;
                redraw();
                onNode.accept(b.node(), b.node().kind == Graph.Kind.EVENT && x >= b.x() + b.w() - 36 && y <= b.y() + Math.max(42,
                    24 + b.size().title().size() * 18));
                return;
            }
            if (edgeSelection.click(result.lines, x, y, viewport.scale, 6)) {
                redraw();
                onEdge.accept(edgeSelection.selected());
            }
        });
        canvas.setOnMouseMoved(e -> {
            if (result == null) return;
            double x =(e.getX() - viewport.panX) / viewport.scale, y =(e.getY() - viewport.panY) / viewport.scale;
            String label = e.getX() >= 8 && e.getX() <= 64 ? result.sectors.stream().filter(s -> y >= s.y() && y <= s.y() + s.height()).map(Sector::label).findFirst().orElse(""): "";
            turnTooltip.setText(label);
            if (label.isBlank()) turnTooltip.hide();
            if (!result.sectors.isEmpty() && e.getX()<64) {
                setCursor(javafx.scene.Cursor.DEFAULT);
                return;
            }
            Box b = result.hit(x, y);
            if (b != null) {
                setCursor(javafx.scene.Cursor.HAND);
                return;
            }
            setCursor(EdgeHitTest.candidates(result.lines, x, y, viewport.scale, 6).size() == 1 ? javafx.scene.Cursor.HAND: javafx.scene.Cursor.OPEN_HAND);
        });
    }
    @Override
    protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        redraw();
    }
    public Result result() {
        return result;
    }
    public void setResult(Result r, boolean reset) {
        if (!reset && result != null && selected != null) {
            Box old = result.byId.get(selected), next = r.byId.get(selected);
            if (old != null && next != null) {
                viewport.panX +=(old.x() - next.x()) * viewport.scale;
                viewport.panY +=(old.y() - next.y()) * viewport.scale;
            }
        }
        result = r;
        edgeSelection.retainEdges(r.lines.stream().map(Line::edge).toList());
        if (reset) {
            viewport.scale =.85;
            viewport.panX = 48;
            viewport.panY = 40;
            if (!r.boxes.isEmpty()) focus(r.boxes.getFirst().node().id);
        }
        redraw();
    }
    public void setSpeakerColors(boolean value) {
        speakerColors = value;
        redraw();
    }
    public void setCampaignSearch(String query) {
        campaignQuery = Objects.requireNonNullElse(query, "").trim();
        redraw();
    }
    public void setCampaignFilters(String query, String ignoredLegacyType) {
        setCampaignSearch(query);
    }
    public static boolean campaignMatch(Graph.Node node, String query) {
        String q = Objects.requireNonNullElse(query, "").trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return false;
        if (node.kind == Graph.Kind.JUNCTION || node.kind == Graph.Kind.CONTROL || node.kind == Graph.Kind.TERMINAL) return false;
        String text = node.title + " " + node.text + " " + node.metadata;
        if (node.item != null) text += " " + node.item.internalName() + " " + node.item.path() + " " + node.item.condition();
        return text.toLowerCase(Locale.ROOT).contains(q);
    }
    public void zoom(double factor) {
        zoomAt(factor, getWidth() / 2, getHeight() / 2);
    }
    private void zoomAt(double factor, double x, double y) {
        viewport.zoomAt(factor, x, y);
        redraw();
    }
    public void fit() {
        if (result == null) return;
        viewport.fit(getWidth(), getHeight(), result.width, result.height);
        redraw();
    }
    public void focus(String id) {
        focus(id,.9);
    }
    private void focus(String id, double scale) {
        if (result == null) return;
        Box b = result.byId.get(id);
        if (b == null) return;
        viewport.focus(b.cx(), b.y(), getWidth(), scale);
        selected = id;
        redraw();
    }
    public void actualSize() {
        if (result != null && !result.boxes.isEmpty()) focus(selected == null ? result.boxes.getFirst().node().id: selected,
            1);
    }
    public double zoom() {
        return viewport.scale;
    }
    public double panX() {
        return viewport.panX;
    }
    public double panY() {
        return viewport.panY;
    }
    public String selected() {
        return selected;
    }
    private Color color(Graph.Node n) {
        return n.kind == Graph.Kind.CHARACTER && speakerColors && speakerPalette.containsKey(n.speaker) ? Theme.speakerFill(speakerPalette.get(n.speaker)): Theme.semanticFill(n);
    }
    public void redraw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setTransform(1, 0, 0, 1, 0, 0);
        g.setFill(Color.web(Theme.BACKGROUND));
        g.fillRect(0, 0, getWidth(), getHeight());
        if (result == null) return;
        if (result.boxes.isEmpty()) {
            g.setFill(Color.web(Theme.MUTED));
            g.setFont(Font.font(16));
            g.fillText("No items match these filters. Select more types or clear the search.", 40, 70);
            onStatus.accept("0 matching items");
            return;
        }
        g.save();
        g.translate(viewport.panX, viewport.panY);
        g.scale(viewport.scale, viewport.scale);
        double left = -viewport.panX / viewport.scale, top = -viewport.panY / viewport.scale, vw = getWidth() / viewport.scale, vh = getHeight() / viewport.scale;
        int sectorIndex = 0;
        for (var s: result.sectors) {
            boolean alt = sectorIndex++ % 2 == 0;
            if (s.y() + s.height()<top || s.y()> top + vh) continue;
            g.setFill(Color.web(alt ? Theme.BAND_DARK: Theme.BAND_LIGHT));
            g.fillRect(left, s.y(), vw, s.height());
            g.setStroke(Color.web(Theme.BORDER));
            g.setLineWidth(1 / viewport.scale);
            g.strokeLine(left, s.y(), left + vw, s.y());
            if (viewport.scale>.08) {
                double visibleTop = Math.max(s.y(), top), visibleBottom = Math.min(s.y() + s.height(), top + vh);
                double limit = Math.min(640, Math.max(0,(visibleBottom - visibleTop) * viewport.scale - 48));
                String label = textMetrics.ellipsize(s.label(), limit, TextMeasurer.TURN_LABEL);
                double measured = textMetrics.width(label, TextMeasurer.TURN_LABEL);
                g.save();
                g.translate(left + 42 / viewport.scale,(visibleTop + visibleBottom + measured / viewport.scale) / 2);
                g.rotate(-90);
                g.scale(1 / viewport.scale, 1 / viewport.scale);
                g.setFill(Color.web(Theme.TURN_TEXT));
                g.setFont(TextMeasurer.TURN_LABEL);
                g.fillText(label, 0, 0);
                g.restore();
            }
        }
        if (!result.sectors.isEmpty()) {
            g.beginPath();
            g.rect(left + 64 / viewport.scale, top, Math.max(0, vw - 64 / viewport.scale), vh);
            g.clip();
        }
        for (var group: result.groups) {
            if (group.bottom()<top || group.y()> top + vh || group.x() + group.width()<left || group.x()> left + vw) continue;
            g.setFill(Color.web(Theme.GROUP));
            g.fillRoundRect(group.x(), group.y(), group.width(), group.height(), 20, 20);
            g.setStroke(Color.web(Theme.GROUP_BORDER));
            g.setLineWidth(1.3);
            g.strokeRoundRect(group.x(), group.y(), group.width(), group.height(), 20, 20);
        }
        Graph.Edge selectedEdge = edgeSelection.selected();
        for (var line: result.lines) if (line.edge() != selectedEdge) drawEdge(g, line, false, top, vh);
        for (var line: result.lines) if (line.edge() == selectedEdge) drawEdge(g, line, true, top, vh);
        g.setLineDashes();
        var visible = result.visible(left, top, vw, vh);
        for (var b: visible) drawNode(g, b);
        g.restore();
        long visibleCards = visible.stream().filter(b -> b.node().kind != Graph.Kind.JUNCTION).count(), allCards = result.boxes.stream().filter(b -> b.node().kind != Graph.Kind.JUNCTION).count();
        String matches = result.graph.campaign != null && !campaignQuery.isEmpty() ? String.format(Locale.ROOT, "   ·   %,d search matches",
            result.graph.nodes.stream().filter(n -> campaignMatch(n, campaignQuery)).count()): "";
        onStatus.accept(String.format(Locale.ROOT, "%,d / %,d boxes visible   ·   %.0f%%%s", visibleCards, allCards,
            viewport.scale * 100, matches));
    }
    private void drawEdge(GraphicsContext g, Line line, boolean highlighted, double top, double vh) {
        var f = line.from();
        var t = line.to();
        boolean back = line.edge().back;
        double minY = Math.min(f.bottom(), t.y()), maxY = Math.max(f.bottom(), t.y());
        if (maxY + 25<top || minY - 25> top + vh) return;
        g.setStroke(Color.web(highlighted ? Theme.ACCENT: back ? Theme.BACK_EDGE: Theme.EDGE));
        g.setLineWidth(highlighted ? Math.max(3.2, 2.4 / viewport.scale): Math.max(1.2, 1 / viewport.scale));
        g.setLineDashes(back ? new double[] {
            6, 5
        }
        : new double[] {
        });
        g.beginPath();
        boolean first = true;
        for (var point: line.points()) {
            if (first) {
                g.moveTo(point.x(), point.y());
                first = false;
            } else g.lineTo(point.x(), point.y());
        }
        g.stroke();
        var end = line.points().getLast();
        if ((result.sectors.isEmpty() || result.graph.campaign != null) && viewport.scale>.18 && t.node().kind != Graph.Kind.JUNCTION) {
            g.setLineDashes();
            g.strokeLine(end.x() - 4, end.y() - 7, end.x(), end.y());
            g.strokeLine(end.x() + 4, end.y() - 7, end.x(), end.y());
        }
        boolean booleanBranch = result.graph.campaign != null;
        if (viewport.scale>.45 &&(booleanBranch || line.edge().from.equals(selected)) && !line.edge().label.isBlank()) {
            String label = textMetrics.ellipsize(line.edge().label, 250, TextMeasurer.EDGE_LABEL);
            Point anchor = end;
            if (booleanBranch && line.points().size()>2) {
                anchor = line.points().get(1);
                for (int i = 1; i<line.points().size(); i++) {
                    Point a = line.points().get(i - 1), b = line.points().get(i);
                    if (a.y() == b.y() && Math.abs(a.x() - b.x())>50) {
                        anchor = new Point((a.x() + b.x()) / 2, a.y() + 20);
                        break;
                    }
                }
            }
            g.setFont(TextMeasurer.EDGE_LABEL);
            double w = textMetrics.width(label, TextMeasurer.EDGE_LABEL);
            g.setFill(Color.web(Theme.BACKGROUND));
            g.fillRoundRect(anchor.x() + 4, anchor.y() - 17, w + 10, 19, 5, 5);
            g.setFill(Color.web(highlighted ? Theme.ACCENT: Theme.TEXT));
            g.fillText(label, anchor.x() + 9, anchor.y() - 3);
        }
    }
    private void drawNode(GraphicsContext g, Box b) {
        var n = b.node();
        if (n.kind == Graph.Kind.JUNCTION) {
            if (!n.type.equals("Type projection") && result.groups.stream().noneMatch(group -> group.entryId().equals(n.id) ||
                group.exitId().equals(n.id))) {
                g.setFill(Color.web(Theme.EDGE));
                g.fillOval(b.cx() - 2, b.y() - 1, 4, 4);
            }
            return;
        }
        boolean rooted = result.graph.campaign != null, match = rooted && campaignMatch(n, campaignQuery);
        g.save();
        g.setFill(match ? Color.web(Theme.SEARCH): color(n));
        g.fillRoundRect(b.x(), b.y(), b.w(), b.h(), 12, 12);
        String border = n.id.equals(selected) ? Theme.ACCENT: match ? Theme.SEARCH_BORDER: n.kind == Graph.Kind.CONDITION ? "#ae83c6": rooted &&
            n.kind == Graph.Kind.NOTICE ? "#cda269": Theme.BORDER;
        g.setStroke(Color.web(border));
        g.setLineWidth(n.id.equals(selected) || match ? 2.4: 1);
        g.strokeRoundRect(b.x(), b.y(), b.w(), b.h(), 12, 12);
        if (viewport.scale<.13) {
            g.restore();
            return;
        }
        double clipTop = -viewport.panY / viewport.scale - 22, clipBottom =(getHeight() - viewport.panY) / viewport.scale + 22;
        double y = b.y() + 22;
        g.setFill(Color.web(Theme.TEXT));
        g.setFont(TextMeasurer.TITLE);
        for (String line: b.size().title()) {
            if (y >= clipTop && y <= clipBottom) g.fillText(line, b.x() + 16, y);
            y += 18;
        }
        if (n.kind == Graph.Kind.EVENT) {
            g.setStroke(Color.web(Theme.BORDER));
            g.strokeLine(b.x() + b.w() - 36, b.y(), b.x() + b.w() - 36, b.y() + Math.max(42, 24 + b.size().title().size() * 18));
            g.setFont(Font.font(19));
            g.fillText(b.size().metadata().isEmpty() ? "›": "⌄", b.x() + b.w() - 25, b.y() + 27);
        }
        if (!b.size().body().isEmpty()) {
            y += 12;
            g.setFont(TextMeasurer.BODY);
            for (String line: b.size().body()) {
                if (y >= clipTop && y <= clipBottom) g.fillText(line, b.x() + 16, y);
                y += 20;
            }
        }
        if (!b.size().metadata().isEmpty()) {
            y += 14;
            g.setFont(TextMeasurer.META);
            g.setFill(Color.web(Theme.MUTED));
            for (String line: b.size().metadata()) {
                if (y >= clipTop && y <= clipBottom) g.fillText(line, b.x() + 16, y);
                y += 17;
            }
        }
        g.restore();
    }
}
