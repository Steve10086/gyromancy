package com.astune.gyromancy.client.canvas;

/**
 * Matches the vanilla mouse handler's fractional scroll accumulation before
 * passing a whole-step direction to {@code Inventory.swapPaint}.
 */
final class CanvasHotbarScroll {
    private double accumulatedX;
    private double accumulatedY;

    int add(double scrollX, double scrollY) {
        if (accumulatedX != 0.0
                && Math.signum(scrollX) != Math.signum(accumulatedX)) {
            accumulatedX = 0.0;
        }
        if (accumulatedY != 0.0
                && Math.signum(scrollY) != Math.signum(accumulatedY)) {
            accumulatedY = 0.0;
        }

        accumulatedX += scrollX;
        accumulatedY += scrollY;
        int wholeX = (int) accumulatedX;
        int wholeY = (int) accumulatedY;
        if (wholeX == 0 && wholeY == 0) return 0;

        accumulatedX -= wholeX;
        accumulatedY -= wholeY;
        return wholeY == 0 ? -wholeX : wholeY;
    }

    void reset() {
        accumulatedX = 0.0;
        accumulatedY = 0.0;
    }
}
