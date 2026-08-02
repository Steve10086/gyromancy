package com.astune.gyromancy.client.canvas;

import com.astune.gyromancy.canvas.CanvasDocument;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanvasStampRasterTest {
    @Test
    void fullSizeStampKeepsDocumentCoordinatesAndSkipsEmptyPixels() {
        int[] colors = new int[16 * 16];
        int[] effects = new int[16 * 16];
        colors[0] = 0xFF112233;
        effects[0] = 7;
        colors[15 * 16 + 15] = 0xFF445566;
        effects[15 * 16 + 15] = 9;
        CanvasDocument stamp = new CanvasDocument(
                1, 1, 1, colors, effects, List.of(), List.of());
        int[] targetColors = new int[16 * 16];
        int[] targetEffects = new int[16 * 16];

        CanvasStampRaster.visit(
                stamp, 1, 1, 16, 16, 8, 8,
                (x, y, color, effect) -> {
                    targetColors[y * 16 + x] = color;
                    targetEffects[y * 16 + x] = effect;
                });

        assertEquals(0xFF112233, targetColors[0]);
        assertEquals(7, targetEffects[0]);
        assertEquals(0xFF445566, targetColors[15 * 16 + 15]);
        assertEquals(9, targetEffects[15 * 16 + 15]);
        assertEquals(0, targetColors[8 * 16 + 8]);
    }

    @Test
    void physicalSizeControlsImpressionSizeOnLargerCanvas() {
        int[] colors = new int[16 * 16];
        int[] effects = new int[16 * 16];
        Arrays.fill(colors, 0xFFFFFFFF);
        Arrays.fill(effects, 1);
        CanvasDocument stamp = new CanvasDocument(
                1, 1, 1, colors, effects, List.of(), List.of());
        int[] visited = {0};

        CanvasStampRaster.visit(
                stamp, 2, 2, 16, 16, 8, 8,
                (x, y, color, effect) -> visited[0]++);

        assertEquals(8 * 8, visited[0]);
    }

    @Test
    void positiveRotationTurnsTheStampClockwise() {
        int[] colors = new int[16 * 16];
        int[] effects = new int[16 * 16];
        colors[0 * 16 + 7] = 0xFF123456;
        effects[0 * 16 + 7] = 3;
        CanvasDocument stamp = new CanvasDocument(
                1, 1, 1, colors, effects, List.of(), List.of());
        int[] targetColors = new int[16 * 16];

        CanvasStampRaster.visit(
                stamp, 1, 1, 16, 16, 8, 8, 90.0, 1.0,
                (x, y, color, effect) -> targetColors[y * 16 + x] = color);

        assertEquals(0xFF123456, targetColors[7 * 16 + 15]);
        assertEquals(0, targetColors[0 * 16 + 7]);
    }

    @Test
    void sizeMultiplierChangesTheVisitedArea() {
        int[] colors = new int[16 * 16];
        int[] effects = new int[16 * 16];
        Arrays.fill(colors, 0xFFFFFFFF);
        Arrays.fill(effects, 1);
        CanvasDocument stamp = new CanvasDocument(
                1, 1, 1, colors, effects, List.of(), List.of());
        int[] visited = {0};

        CanvasStampRaster.visit(
                stamp, 2, 2, 16, 16, 8, 8, 0.0, 0.5,
                (x, y, color, effect) -> visited[0]++);

        assertEquals(4 * 4, visited[0]);
    }
}
