package sordland;

import sordland.graph.Graph;
import sordland.layout.EdgeHitTest;
import sordland.layout.LayoutEngine;
import sordland.ui.EdgeSelection;
import java.util.*;
import static sordland.TestSupport.*;

final class EdgeChecks {
    private EdgeChecks() {
    }
    static void run() {
        Graph.Edge first = edge("a", "b"), second = edge("a", "c");
        var horizontal = line(first, 0, 0, 100, 0);
        var separate = line(second, 0, 40, 100, 40);
        equal(Set.of(first), hit(List.of(horizontal), 50, 5, 1), "Unique nearby segment is a candidate");
        equal(Set.of(), hit(List.of(horizontal), 50, 7, 1), "Outside screen tolerance has no candidate");
        equal(Set.of(first), hit(List.of(horizontal), -5, 0, 1), "Segment endpoint has a circular tolerance region");
        equal(Set.of(), hit(List.of(horizontal), -7, 0, 1), "Infinite extension beyond segment is not selectable");
        var overlap = line(second, 0, 0, 100, 0);
        equal(2, hit(List.of(horizontal, overlap), 50, 0, 1).size(), "Identical segment geometry is ambiguous");
        Graph.Edge sameFields = new Graph.Edge(first.from, first.to, first.label, first.back);
        equal(2, hit(List.of(horizontal, line(sameFields, 0, 0, 100, 0)), 50, 0, 1).size(), "Distinct edge identities remain ambiguous even with identical endpoint metadata");
        equal(1, hit(List.of(horizontal, line(first, 0, 0, 100, 0)), 50, 0, 1).size(), "Repeated polyline for the same edge counts once");
        var crossing = line(second, 50, -50, 50, 50);
        equal(2, hit(List.of(horizontal, crossing), 50, 0, 1).size(), "Crossing polylines are ambiguous at their intersection");
        equal(Set.of(first), hit(List.of(horizontal, crossing), 20, 0, 1), "Unambiguous portion away from crossing is selectable");
        var left = line(first, 50, 0, 50, 50, 0, 50, 0, 100);
        var right = line(second, 50, 0, 50, 50, 100, 50, 100, 100);
        equal(2, hit(List.of(left, right), 50, 25, 1).size(), "Shared outgoing trunk is ambiguous");
        equal(Set.of(first), hit(List.of(left, right), 0, 75, 1), "Unique left branch remains selectable");
        equal(Set.of(second), hit(List.of(left, right), 100, 75, 1), "Unique right branch remains selectable");
        equal(1, hit(List.of(left), 50, 50, 1).size(), "Two segments at a bend deduplicate to one edge");
        var diagonal = line(first, 0, 0, 100, 100);
        equal(Set.of(first), hit(List.of(diagonal), 50, 55, 1), "Distance calculation handles diagonal segments");
        equal(Set.of(), hit(List.of(diagonal), 50, 60, 1), "Diagonal segment rejects points outside perpendicular tolerance");
        var zero = line(first, 20, 20, 20, 20);
        equal(Set.of(first), hit(List.of(zero), 23, 24, 1), "Zero-length segment is safely selectable by point distance");
        equal(Set.of(), hit(List.of(zero), 27, 20, 1), "Zero-length segment rejects distant clicks");
        equal(Set.of(), hit(List.of(line(first, 20, 20)), 20, 20, 1), "Single point without a segment has no connector hit area");
        for (double scale: new double[] {
            .1,.5, 1, 2, 8
        }) {
            equal(Set.of(first), hit(List.of(horizontal), 50, 5 / scale, scale), "Five screen pixels hits consistently at scale " + scale);
            equal(Set.of(), hit(List.of(horizontal), 50, 7 / scale, scale), "Seven screen pixels misses consistently at scale " + scale);
        }
        EdgeSelection selection = new EdgeSelection();
        equal(null, selection.selected(), "No arrow selected initially");
        check(selection.click(List.of(horizontal, separate), 50, 0, 1, 6), "A unique click updates selection");
        equal(first, selection.selected(), "First arrow is highlighted");
        check(!selection.click(List.of(horizontal, separate), 50, 0, 1, 6), "Clicking selected arrow leaves state unchanged");
        check(!selection.click(List.of(horizontal, separate), 200, 200, 1, 6), "Empty click does not change selection");
        equal(first, selection.selected(), "Empty click preserves existing highlight");
        check(!selection.click(List.of(horizontal, overlap), 50, 0, 1, 6), "Overlapping hit does not change selection");
        equal(first, selection.selected(), "Ambiguous click preserves existing highlight");
        check(selection.click(List.of(horizontal, separate), 50, 40, 1, 6), "A different unique arrow replaces selection");
        equal(second, selection.selected(), "Only new arrow remains highlighted");
        check(!selection.click(List.of(left, right), 50, 25, 1, 6), "Shared trunk click preserves selected branch");
        equal(second, selection.selected(), "Shared trunk does not choose an arbitrary edge");
        check(!selection.retainEdges(List.of(first, second)), "Relayout retaining source edge leaves selection intact");
        equal(second, selection.selected(), "Retained edge stays selected");
        check(selection.retainEdges(List.of(first)), "Projection removing selected edge clears highlight");
        equal(null, selection.selected(), "Removed arrow is no longer selected");
        check(!selection.retainEdges(List.of()), "Empty selection needs no update after filtering");
    }
    private static Set<Graph.Edge> hit(List<LayoutEngine.Line> lines, double x, double y, double scale) {
        return EdgeHitTest.candidates(lines, x, y, scale, 6);
    }
    private static Graph.Edge edge(String from, String to) {
        return new Graph.Edge(from, to, "", false);
    }
    private static LayoutEngine.Line line(Graph.Edge edge, double...xy) {
        if (xy.length % 2 != 0) throw new IllegalArgumentException("Coordinates must be x/y pairs");
        List<LayoutEngine.Point> points = new ArrayList<>();
        for (int i = 0; i<xy.length; i += 2) points.add(new LayoutEngine.Point(xy[i], xy[i + 1]));
        return new LayoutEngine.Line(edge, null, null, 0, List.copyOf(points));
    }
}
