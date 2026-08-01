package com.astune.gyromancy.client.canvas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CanvasTexturePixelsTest {
    @Test
    void transparentPixelsBecomeOpaquePaper() {
        assertEquals(
                CanvasTexturePixels.PAPER_ARGB,
                CanvasTexturePixels.compositeOverPaper(0x0024132F));
    }

    @Test
    void opaqueStrokesKeepTheirColor() {
        assertEquals(
                0xFF24132F,
                CanvasTexturePixels.compositeOverPaper(0xFF24132F));
    }

    @Test
    void translucentStrokesAreCompositedOntoPaper() {
        assertEquals(
                0xFF787364,
                CanvasTexturePixels.compositeOverPaper(0x80000000));
    }

    @Test
    void materialAndStrokesAreCompositedBeforeRendering() {
        int[] material = {0xFF112233, 0xFF445566};
        int[] strokes = {0, 0xFFFF0000};

        assertArrayEquals(
                new int[]{0xFF112233, 0xFFFF0000},
                CanvasTexturePixels.composeOverBackground(
                        2, 1, strokes, material, false));
    }

    @Test
    void nearestResizeNeverIntroducesIntermediateColors() {
        int[] source = {
                0xFFFF0000, 0xFF00FF00,
                0xFF0000FF, 0xFFFFFFFF
        };

        assertArrayEquals(
                new int[]{
                        0xFFFF0000, 0xFFFF0000, 0xFF00FF00, 0xFF00FF00,
                        0xFFFF0000, 0xFFFF0000, 0xFF00FF00, 0xFF00FF00,
                        0xFF0000FF, 0xFF0000FF, 0xFFFFFFFF, 0xFFFFFFFF,
                        0xFF0000FF, 0xFF0000FF, 0xFFFFFFFF, 0xFFFFFFFF
                },
                CanvasTexturePixels.resizeNearest(source, 2, 2, 4, 4));
    }

    @Test
    void translucentStrokeCompositesOverMaterial() {
        assertEquals(
                0xFF80007F,
                CanvasTexturePixels.compositeOver(
                        0x80FF0000, 0xFF0000FF));
    }

    @Test
    void compositionCanMirrorOnlyTheTextureXAxis() {
        int[] colors = {0xFFFF0000, 0, 0xFF0000FF};

        assertArrayEquals(
                new int[]{
                        0xFF0000FF,
                        CanvasTexturePixels.PAPER_ARGB,
                        0xFFFF0000
                },
                CanvasTexturePixels.compose(3, 1, colors, true));
        assertArrayEquals(
                new int[]{
                        0xFFFF0000,
                        CanvasTexturePixels.PAPER_ARGB,
                        0xFF0000FF
                },
                CanvasTexturePixels.compose(3, 1, colors, false));
    }

    @Test
    void nativeImageColorConversionUsesAbgr() {
        assertEquals(
                0xFF2F1324,
                CanvasTexturePixels.argbToAbgr(0xFF24132F));
    }
}
