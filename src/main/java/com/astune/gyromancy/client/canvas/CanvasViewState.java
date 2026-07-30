package com.astune.gyromancy.client.canvas;

/** Client-only pan and zoom state for one canvas editor screen. */
final class CanvasViewState {
    static final double MIN_ZOOM = 0.5;
    static final double MAX_ZOOM = 8.0;
    private static final double ZOOM_STEP = 1.2;
    private static final double MIN_VISIBLE_PIXELS = 24.0;

    private double zoom = 1.0;
    private double panX;
    private double panY;

    DisplayRect displayRect(Rect fittedCanvas) {
        double width = fittedCanvas.width * zoom;
        double height = fittedCanvas.height * zoom;
        double x = fittedCanvas.centerX() - width * 0.5 + panX;
        double y = fittedCanvas.centerY() - height * 0.5 + panY;
        return new DisplayRect(x, y, width, height);
    }

    void panBy(double deltaX, double deltaY, Rect fittedCanvas, Rect viewport) {
        panX += deltaX;
        panY += deltaY;
        clamp(fittedCanvas, viewport);
    }

    void zoomAt(double scroll,
                double pointerX,
                double pointerY,
                Rect fittedCanvas,
                Rect viewport) {
        if (scroll == 0.0) return;
        DisplayRect before = displayRect(fittedCanvas);
        pointerX = clamp(pointerX, before.x, before.right());
        pointerY = clamp(pointerY, before.y, before.bottom());
        double normalizedX = (pointerX - before.x) / before.width;
        double normalizedY = (pointerY - before.y) / before.height;

        double nextZoom = clamp(zoom * Math.pow(ZOOM_STEP, scroll), MIN_ZOOM, MAX_ZOOM);
        if (nextZoom == zoom) return;
        zoom = nextZoom;

        double nextWidth = fittedCanvas.width * zoom;
        double nextHeight = fittedCanvas.height * zoom;
        double desiredX = pointerX - normalizedX * nextWidth;
        double desiredY = pointerY - normalizedY * nextHeight;
        panX = desiredX - (fittedCanvas.centerX() - nextWidth * 0.5);
        panY = desiredY - (fittedCanvas.centerY() - nextHeight * 0.5);
        clamp(fittedCanvas, viewport);
    }

    void clamp(Rect fittedCanvas, Rect viewport) {
        DisplayRect display = displayRect(fittedCanvas);
        double visibleX = Math.min(MIN_VISIBLE_PIXELS,
                Math.min(display.width, viewport.width));
        double visibleY = Math.min(MIN_VISIBLE_PIXELS,
                Math.min(display.height, viewport.height));

        if (display.x > viewport.right() - visibleX) {
            panX -= display.x - (viewport.right() - visibleX);
        } else if (display.right() < viewport.x + visibleX) {
            panX += viewport.x + visibleX - display.right();
        }
        if (display.y > viewport.bottom() - visibleY) {
            panY -= display.y - (viewport.bottom() - visibleY);
        } else if (display.bottom() < viewport.y + visibleY) {
            panY += viewport.y + visibleY - display.bottom();
        }
    }

    double zoom() {
        return zoom;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    record Rect(double x, double y, double width, double height) {
        double centerX() {
            return x + width * 0.5;
        }

        double centerY() {
            return y + height * 0.5;
        }

        double right() {
            return x + width;
        }

        double bottom() {
            return y + height;
        }
    }

    record DisplayRect(double x, double y, double width, double height) {
        double right() {
            return x + width;
        }

        double bottom() {
            return y + height;
        }

        boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right()
                    && pointY >= y && pointY < bottom();
        }
    }
}
