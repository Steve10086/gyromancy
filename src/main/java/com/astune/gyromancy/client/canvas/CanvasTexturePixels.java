package com.astune.gyromancy.client.canvas;

/** Pure pixel composition shared by editor and placed-canvas textures. */
final class CanvasTexturePixels {
    static final int PAPER_ARGB = 0xFFF1E6C8;

    private CanvasTexturePixels() {}

    static int[] compose(int width, int height, int[] colors, boolean mirrorX) {
        if (colors.length != width * height) {
            throw new IllegalArgumentException("Canvas color matrix dimensions do not match");
        }
        int[] pixels = new int[colors.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int textureX = textureX(x, width, mirrorX);
                pixels[y * width + textureX] = compositeOverPaper(colors[y * width + x]);
            }
        }
        return pixels;
    }

    static int[] composeOverBackground(int width,
                                       int height,
                                       int[] colors,
                                       int[] background,
                                       boolean mirrorX) {
        if (colors.length != width * height
                || background.length != width * height) {
            throw new IllegalArgumentException(
                    "Canvas and background dimensions do not match");
        }
        int[] pixels = new int[colors.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int textureX = textureX(x, width, mirrorX);
                pixels[y * width + textureX] =
                        compositeOver(colors[index], background[index]);
            }
        }
        return pixels;
    }

    static int[] resizeNearest(int[] source,
                               int sourceWidth,
                               int sourceHeight,
                               int targetWidth,
                               int targetHeight) {
        if (source.length != sourceWidth * sourceHeight
                || sourceWidth <= 0
                || sourceHeight <= 0
                || targetWidth <= 0
                || targetHeight <= 0) {
            throw new IllegalArgumentException("Invalid image dimensions");
        }
        int[] result = new int[targetWidth * targetHeight];
        for (int y = 0; y < targetHeight; y++) {
            int sourceY = y * sourceHeight / targetHeight;
            for (int x = 0; x < targetWidth; x++) {
                int sourceX = x * sourceWidth / targetWidth;
                result[y * targetWidth + x] =
                        source[sourceY * sourceWidth + sourceX];
            }
        }
        return result;
    }

    static int textureX(int matrixX, int width, boolean mirrorX) {
        return mirrorX
                ? CanvasEditorCoordinates.screenColumnForMatrixColumn(matrixX, width)
                : matrixX;
    }

    static int compositeOverPaper(int strokeArgb) {
        return compositeOver(strokeArgb, PAPER_ARGB);
    }

    static int compositeOver(int foregroundArgb, int backgroundArgb) {
        int foregroundAlpha = foregroundArgb >>> 24;
        if (foregroundAlpha == 0) return backgroundArgb;
        if (foregroundAlpha == 0xFF) return foregroundArgb;

        int backgroundAlpha = backgroundArgb >>> 24;
        int inverseAlpha = 0xFF - foregroundAlpha;
        long outputAlphaNumerator =
                (long) foregroundAlpha * 0xFF
                        + (long) backgroundAlpha * inverseAlpha;
        if (outputAlphaNumerator == 0) return 0;

        int outputAlpha = (int) ((outputAlphaNumerator + 127) / 255);
        int red = compositeChannel(
                foregroundArgb >> 16,
                backgroundArgb >> 16,
                foregroundAlpha,
                backgroundAlpha,
                inverseAlpha,
                outputAlphaNumerator);
        int green = compositeChannel(
                foregroundArgb >> 8,
                backgroundArgb >> 8,
                foregroundAlpha,
                backgroundAlpha,
                inverseAlpha,
                outputAlphaNumerator);
        int blue = compositeChannel(
                foregroundArgb,
                backgroundArgb,
                foregroundAlpha,
                backgroundAlpha,
                inverseAlpha,
                outputAlphaNumerator);
        return outputAlpha << 24 | red << 16 | green << 8 | blue;
    }

    static int argbToAbgr(int argb) {
        int alpha = argb & 0xFF000000;
        int red = (argb >> 16) & 0xFF;
        int green = argb & 0x0000FF00;
        int blue = argb & 0xFF;
        return alpha | blue << 16 | green | red;
    }

    private static int compositeChannel(int foreground,
                                        int background,
                                        int foregroundAlpha,
                                        int backgroundAlpha,
                                        int inverseAlpha,
                                        long outputAlphaNumerator) {
        long numerator =
                (long) (foreground & 0xFF) * foregroundAlpha * 0xFF
                        + (long) (background & 0xFF)
                        * backgroundAlpha * inverseAlpha;
        return (int) ((numerator + outputAlphaNumerator / 2)
                / outputAlphaNumerator);
    }
}
