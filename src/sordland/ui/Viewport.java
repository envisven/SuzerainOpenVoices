package sordland.ui;

public final class Viewport {
    double scale =.8, panX = 40, panY = 40;
    public record State(double scale, double panX, double panY) {
    }
    public State snapshot() {
        return new State(scale, panX, panY);
    }
    public void restore(State state) {
        scale = state.scale;
        panX = state.panX;
        panY = state.panY;
    }
    public double worldX(double screenX) {
        return(screenX - panX) / scale;
    }
    public double worldY(double screenY) {
        return(screenY - panY) / scale;
    }
    public void panBy(double dx, double dy) {
        panX += dx;
        panY += dy;
    }
    public void zoomAt(double factor, double x, double y) {
        double next = Math.clamp(scale * factor,.000001, 3.5);
        panX = x -(x - panX) * next / scale;
        panY = y -(y - panY) * next / scale;
        scale = next;
    }
    public void fit(double width, double height, double contentWidth, double contentHeight) {
        scale = Math.clamp(Math.min(Math.max(1, width - 64) / Math.max(1, contentWidth), Math.max(1, height - 64) / Math.max(1,
            contentHeight)),.000001, 2);
        panX =(width - contentWidth * scale) / 2;
        panY =(height - contentHeight * scale) / 2;
    }
    public void focus(double centerX, double top, double viewportWidth, double readableScale) {
        scale = readableScale;
        panX = viewportWidth / 2 - centerX * scale;
        panY = 55 - top * scale;
    }
}
