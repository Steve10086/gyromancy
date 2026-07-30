package com.astune.gyromancy.client.canvas;

/**
 * Translates between GUI columns and the canvas matrix's horizontal axis.
 *
 * <p>The stored canvas raster follows the hanging-canvas convention, whose X
 * axis points opposite to screen X. Keeping this conversion at the editor
 * boundary leaves recognition, compilation, and world transforms unchanged.
 */
public final class CanvasEditorCoordinates {
    private CanvasEditorCoordinates() {}

    public static int matrixColumnForScreenColumn(int screenColumn, int width) {
        return screenColumn;
    }

    public static int screenColumnForMatrixColumn(int matrixColumn, int width) {
        return width - 1 - matrixColumn;
    }
}
