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

    static int textureX(int matrixX, int width, boolean mirrorX) {
        return mirrorX
                ? CanvasEditorCoordinates.screenColumnForMatrixColumn(matrixX, width)
                : matrixX;
    }

    static int compositeOverPaper(int strokeArgb) {
        int alpha = strokeArgb >>> 24;
        if (alpha == 0) return PAPER_ARGB;
        if (alpha == 0xFF) return strokeArgb;

        int inverseAlpha = 0xFF - alpha;
        int red = blendChannel(strokeArgb >> 16, PAPER_ARGB >> 16, alpha, inverseAlpha);
        int green = blendChannel(strokeArgb >> 8, PAPER_ARGB >> 8, alpha, inverseAlpha);
        int blue = blendChannel(strokeArgb, PAPER_ARGB, alpha, inverseAlpha);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    static int argbToAbgr(int argb) {
        int alpha = argb & 0xFF000000;
        int red = (argb >> 16) & 0xFF;
        int green = argb & 0x0000FF00;
        int blue = argb & 0xFF;
        return alpha | blue << 16 | green | red;
    }

    private static int blendChannel(int foreground,
                                    int background,
                                    int alpha,
                                    int inverseAlpha) {
        return (((foreground & 0xFF) * alpha)
                + ((background & 0xFF) * inverseAlpha)
                + 127) / 255;
    }
}
