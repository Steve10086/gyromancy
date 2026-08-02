package com.astune.gyromancy.client.canvas;

import com.astune.gyromancy.canvas.CanvasDocument;

/** Pure coordinate conversion for placing stamp documents in an editor raster. */
final class CanvasStampRaster {
    private CanvasStampRaster() {}

    static void visit(CanvasDocument stamp,
                      int targetPhysicalWidth,
                      int targetPhysicalHeight,
                      int targetRasterWidth,
                      int targetRasterHeight,
                      int centerX,
                      int centerY,
                      PixelVisitor visitor) {
        visit(stamp,
                targetPhysicalWidth, targetPhysicalHeight,
                targetRasterWidth, targetRasterHeight,
                centerX, centerY, 0.0, 1.0, visitor);
    }

    static void visit(CanvasDocument stamp,
                      int targetPhysicalWidth,
                      int targetPhysicalHeight,
                      int targetRasterWidth,
                      int targetRasterHeight,
                      int centerX,
                      int centerY,
                      double clockwiseDegrees,
                      double sizeMultiplier,
                      PixelVisitor visitor) {
        if (!Double.isFinite(sizeMultiplier) || sizeMultiplier <= 0.0) {
            throw new IllegalArgumentException(
                    "Stamp size multiplier must be positive and finite");
        }
        double impressionWidth = (double) stamp.physicalWidth()
                * targetRasterWidth / targetPhysicalWidth * sizeMultiplier;
        double impressionHeight = (double) stamp.physicalHeight()
                * targetRasterHeight / targetPhysicalHeight * sizeMultiplier;
        double radians = Math.toRadians(clockwiseDegrees);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        double halfWidth = impressionWidth * 0.5;
        double halfHeight = impressionHeight * 0.5;
        double extentX = Math.abs(cosine) * halfWidth
                + Math.abs(sine) * halfHeight;
        double extentY = Math.abs(sine) * halfWidth
                + Math.abs(cosine) * halfHeight;
        double centerPixelX = centerX;
        double centerPixelY = centerY;
        int firstX = Math.max(0,
                (int) Math.floor(centerPixelX - extentX));
        int firstY = Math.max(0,
                (int) Math.floor(centerPixelY - extentY));
        int lastX = Math.min(targetRasterWidth,
                (int) Math.ceil(centerPixelX + extentX));
        int lastY = Math.min(targetRasterHeight,
                (int) Math.ceil(centerPixelY + extentY));

        int sourceWidth = stamp.resolutionWidth();
        int sourceHeight = stamp.resolutionHeight();
        int[] sourceColors = stamp.colors();
        int[] sourceEffects = stamp.strokeEffects();
        for (int y = firstY; y < lastY; y++) {
            double deltaY = y + 0.5 - centerPixelY;
            for (int x = firstX; x < lastX; x++) {
                double deltaX = x + 0.5 - centerPixelX;
                double localX = cosine * deltaX + sine * deltaY;
                double localY = -sine * deltaX + cosine * deltaY;
                double normalizedX = localX / impressionWidth + 0.5;
                double normalizedY = localY / impressionHeight + 0.5;
                if (normalizedX < 0.0 || normalizedX >= 1.0) continue;
                if (normalizedY < 0.0 || normalizedY >= 1.0) continue;
                int sourceX = Math.min(sourceWidth - 1,
                        (int) (normalizedX * sourceWidth));
                int sourceY = Math.min(sourceHeight - 1,
                        (int) (normalizedY * sourceHeight));
                int sourceIndex = sourceY * sourceWidth + sourceX;
                int color = sourceColors[sourceIndex];
                int effect = sourceEffects[sourceIndex];
                if ((color >>> 24) == 0 && effect == 0) continue;
                visitor.visit(x, y, color, effect);
            }
        }
    }

    @FunctionalInterface
    interface PixelVisitor {
        void visit(int x, int y, int color, int effect);
    }
}
