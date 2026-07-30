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
