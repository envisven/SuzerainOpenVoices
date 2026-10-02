package sordland;

import sordland.ui.Viewport;
import static sordland.TestSupport.*;

final class ViewportChecks {
    static void run() {
        var viewport = new Viewport();
        viewport.panBy(-235, 410);
        double x = viewport.worldX(350), y = viewport.worldY(270);
        viewport.zoomAt(1.5, 350, 270);
        near(x, viewport.worldX(350), "Wheel zoom preserves world position under pointer");
        near(y, viewport.worldY(270), "Wheel zoom preserves vertical pointer anchor");
        var campaign = viewport.snapshot();
        viewport.focus(300, 1000, 1200,.9);
        viewport.restore(campaign);
        equal(campaign, viewport.snapshot(), "Retained viewport restores exact pan and zoom");
        viewport.fit(1200, 800, 3000, 4000);
        var fit = viewport.snapshot();
        check(fit.panX() >= 31.99 && fit.panY() >= 31.99, "Fit leaves margins around content");
        check(3000 * fit.scale() + fit.panX() <= 1200 && 4000 * fit.scale() + fit.panY() <= 800, "Fit contains full graph bounds");
        viewport.focus(300, 1000, 1200, 1);
        near(1, viewport.snapshot().scale(), "Readable selects actual 100 percent scale");
        near(300, viewport.worldX(600), "Focused node is horizontally centered");
        near(1000, viewport.worldY(55), "Focused node is placed below viewport margin");
        viewport.fit(0, 0, 0, 0);
        check(Double.isFinite(viewport.snapshot().scale()), "Empty or not-yet-sized viewport is finite");
    }
    private static void near(double expected, double actual, String message) {
        check(Math.abs(expected - actual)<.00001, message);
    }
}
