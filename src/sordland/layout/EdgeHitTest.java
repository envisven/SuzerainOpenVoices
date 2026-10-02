package sordland.layout;

import sordland.graph.Graph;
import java.util.*;

public final class EdgeHitTest {
    private EdgeHitTest() {
    }
    public static Set<Graph.Edge> candidates(List<LayoutEngine.Line> lines, double worldX, double worldY, double scale,
        double tolerancePixels) {
        Objects.requireNonNull(lines, "lines");
        if (!Double.isFinite(worldX) || !Double.isFinite(worldY)) throw new IllegalArgumentException("Click coordinates must be finite");
        if (!Double.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("Viewport scale must be finite and positive");
        if (!Double.isFinite(tolerancePixels) || tolerancePixels<0) throw new IllegalArgumentException("Hit tolerance must be finite and non-negative");
        double tolerance = tolerancePixels / scale;
        Set<Graph.Edge> result = Collections.newSetFromMap(new IdentityHashMap<>());
        for (LayoutEngine.Line line: lines) {
            if (result.contains(line.edge())) continue;
            List<LayoutEngine.Point> points = line.points();
            for (int i = 1; i<points.size(); i++) {
                if (distance(worldX, worldY, points.get(i - 1), points.get(i)) <= tolerance) {
                    result.add(line.edge());
                    break;
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }
    private static double distance(double x, double y, LayoutEngine.Point a, LayoutEngine.Point b) {
        double dx = b.x() - a.x(), dy = b.y() - a.y();
        double length = Math.hypot(dx, dy);
        if (length == 0) return Math.hypot(x - a.x(), y - a.y());
        double unitX = dx / length, unitY = dy / length;
        double along = Math.max(0, Math.min(length,(x - a.x()) * unitX +(y - a.y()) * unitY));
        return Math.hypot(x -(a.x() + along * unitX), y -(a.y() + along * unitY));
    }
}
