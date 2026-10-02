package sordland.ui;

import sordland.graph.Graph;
import sordland.layout.EdgeHitTest;
import sordland.layout.LayoutEngine;
import java.util.*;

public final class EdgeSelection {
    private Graph.Edge selected;
    public Graph.Edge selected() {
        return selected;
    }
    public boolean click(List<LayoutEngine.Line> lines, double worldX, double worldY, double scale, double tolerancePixels) {
        Set<Graph.Edge> candidates = EdgeHitTest.candidates(lines, worldX, worldY, scale, tolerancePixels);
        if (candidates.size() != 1) return false;
        Graph.Edge next = candidates.iterator().next();
        if (next == selected) return false;
        selected = next;
        return true;
    }
    public boolean retainEdges(Collection<Graph.Edge> edges) {
        Objects.requireNonNull(edges, "edges");
        if (selected == null) return false;
        for (Graph.Edge edge: edges) if (edge == selected) return false;
        selected = null;
        return true;
    }
}
