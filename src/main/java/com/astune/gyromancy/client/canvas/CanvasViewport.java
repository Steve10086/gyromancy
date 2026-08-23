package com.astune.gyromancy.client.canvas;

/**
 * Shared viewport controller for canvas-based screens.
 *
 * <p>The viewport owns the fitted canvas geometry, display transform and
 * Ctrl-modified view gestures. Painting and item tools remain owned by the
 * screen or the selected item; they never participate in view navigation.
 */
final class CanvasViewport {
    private final CanvasViewState viewState = new CanvasViewState();
    private CanvasViewState.Rect fittedCanvas = new CanvasViewState.Rect(0, 0, 1, 1);
    private CanvasViewState.Rect viewport = new CanvasViewState.Rect(0, 0, 1, 1);
    private boolean panning;

    void layout(CanvasViewState.Rect fittedCanvas,
                CanvasViewState.Rect viewport) {
        this.fittedCanvas = fittedCanvas;
        this.viewport = viewport;
        viewState.clamp(fittedCanvas, viewport);
    }

    CanvasViewState.DisplayRect displayRect() {
        return viewState.displayRect(fittedCanvas);
    }

    boolean isOverViewport(double mouseX, double mouseY) {
        return mouseX >= viewport.x()
                && mouseX < viewport.right()
                && mouseY >= viewport.y()
                && mouseY < viewport.bottom();
    }

    boolean isOverCanvas(double mouseX, double mouseY) {
        return isOverViewport(mouseX, mouseY)
                && displayRect().contains(mouseX, mouseY);
    }

    int[] canvasPixelAt(double mouseX,
                       double mouseY,
                       int rasterWidth,
                       int rasterHeight) {
        if (rasterWidth <= 0 || rasterHeight <= 0) return new int[]{0, 0};
        CanvasViewState.DisplayRect display = displayRect();
        int x = Math.min(rasterWidth - 1, Math.max(0,
                (int) ((mouseX - display.x()) * rasterWidth / display.width())));
        int y = Math.min(rasterHeight - 1, Math.max(0,
                (int) ((mouseY - display.y()) * rasterHeight / display.height())));
        return new int[]{x, y};
    }

    boolean mouseClicked(double mouseX,
                         double mouseY,
                         int button,
                         boolean controlDown) {
        if (!controlDown || button != 0 || !isOverViewport(mouseX, mouseY)) {
            return false;
        }
        panning = true;
        return true;
    }

    boolean mouseDragged(int button, double dragX, double dragY) {
        if (!panning || button != 0) return false;
        panView(dragX, dragY);
        return true;
    }

    boolean mouseReleased(int button) {
        if (!panning || button != 0) return false;
        panning = false;
        return true;
    }

    boolean mouseScrolled(double mouseX,
                          double mouseY,
                          double scrollX,
                          double scrollY,
                          boolean controlDown) {
        if (!controlDown || !isOverViewport(mouseX, mouseY)) return false;
        double scroll = scrollY != 0.0 ? scrollY : -scrollX;
        if (scroll == 0.0) return false;
        zoomView(scroll, mouseX, mouseY);
        return true;
    }

    boolean isPanning() {
        return panning;
    }

    void cancelPan() {
        panning = false;
    }

    void panView(double deltaX, double deltaY) {
        viewState.panBy(deltaX, deltaY, fittedCanvas, viewport);
    }

    void zoomView(double scroll, double mouseX, double mouseY) {
        viewState.zoomAt(scroll,
                Math.max(viewport.x(), Math.min(viewport.right(), mouseX)),
                Math.max(viewport.y(), Math.min(viewport.bottom(), mouseY)),
                fittedCanvas,
                viewport);
    }

    double zoom() {
        return viewState.zoom();
    }
}
