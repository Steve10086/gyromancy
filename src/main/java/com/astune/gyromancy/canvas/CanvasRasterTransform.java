package com.astune.gyromancy.canvas;

/**
 * Pure whole-raster transforms used by the canvas editor.
 *
 * <p>The raster is always a square matrix, so a quarter turn keeps its
 * dimensions. Transforms work in document coordinates: row 0 is the top row
 * and column 0 is the leftmost column of the displayed canvas.
 */
public final class CanvasRasterTransform {
    private CanvasRasterTransform() {}

    public static int[] rotateClockwise(int[] source, int size) {
        requireSquare(source, size);
        int[] result = new int[source.length];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                result[y * size + x] = source[(size - 1 - x) * size + y];
            }
        }
        return result;
    }

    public static int[] rotateCounterClockwise(int[] source, int size) {
        requireSquare(source, size);
        int[] result = new int[source.length];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                result[y * size + x] = source[x * size + (size - 1 - y)];
            }
        }
        return result;
    }

    /** Flips the raster left to right. */
    public static int[] mirrorHorizontally(int[] source, int size) {
        requireSquare(source, size);
        int[] result = new int[source.length];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                result[y * size + x] = source[y * size + (size - 1 - x)];
            }
        }
        return result;
    }

    /** Flips the raster top to bottom. */
    public static int[] mirrorVertically(int[] source, int size) {
        requireSquare(source, size);
        int[] result = new int[source.length];
        for (int y = 0; y < size; y++) {
            System.arraycopy(source, (size - 1 - y) * size, result, y * size, size);
        }
        return result;
    }

    private static void requireSquare(int[] source, int size) {
        if (size <= 0 || source.length != size * size) {
            throw new IllegalArgumentException("Canvas raster must be a non-empty square matrix");
        }
    }
}
