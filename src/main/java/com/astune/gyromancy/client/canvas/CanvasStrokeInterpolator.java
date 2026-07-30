package com.astune.gyromancy.client.canvas;

/** Integer raster interpolation used between consecutive mouse samples. */
final class CanvasStrokeInterpolator {
    private CanvasStrokeInterpolator() {}

    static void visitLine(int startX, int startY,
                          int endX, int endY,
                          PixelConsumer consumer) {
        int x = startX;
        int y = startY;
        int deltaX = Math.abs(endX - startX);
        int stepX = startX < endX ? 1 : -1;
        int deltaY = -Math.abs(endY - startY);
        int stepY = startY < endY ? 1 : -1;
        int error = deltaX + deltaY;

        while (true) {
            consumer.accept(x, y);
            if (x == endX && y == endY) return;
            int doubledError = error * 2;
            if (doubledError >= deltaY) {
                error += deltaY;
                x += stepX;
            }
            if (doubledError <= deltaX) {
                error += deltaX;
                y += stepY;
            }
        }
    }

    @FunctionalInterface
    interface PixelConsumer {
        void accept(int x, int y);
    }
}
