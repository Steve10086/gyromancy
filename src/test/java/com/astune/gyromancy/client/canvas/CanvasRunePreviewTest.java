package com.astune.gyromancy.client.canvas;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasRunePreviewTest {
    @Test
    void matchedCellsUseRuneColorWithoutChangingTheDocumentPixels() {
        CanvasRunePreview preview = CanvasRunePreview.of(
                3, 2,
                List.of(new CanvasRunePreview.RuneMatch(
                        ResourceLocation.fromNamespaceAndPath("gyromancy", "fire"),
                        0.95F,
                        0xFFFF8888,
                        new int[]{1, 4})));
        int[] documentColors = {
                0xFF111111, 0xFF222222, 0xFF333333,
                0xFF444444, 0xFF555555, 0xFF666666
        };

        assertArrayEquals(new int[]{
                0xFF111111, 0xFFFF8888, 0xFF333333,
                0xFF444444, 0xFFFF8888, 0xFF666666
        }, preview.colorize(documentColors));
        assertArrayEquals(new int[]{
                0xFF111111, 0xFF222222, 0xFF333333,
                0xFF444444, 0xFF555555, 0xFF666666
        }, documentColors);
    }

    @Test
    void hoveringOnlyReportsPixelsOwnedByTheMatchedRune() {
        ResourceLocation fire = ResourceLocation.fromNamespaceAndPath(
                "gyromancy", "fire");
        CanvasRunePreview preview = CanvasRunePreview.of(
                2, 2,
                List.of(new CanvasRunePreview.RuneMatch(
                        fire, 0.9F, 0xFFFF8888, new int[]{2})));

        assertTrue(preview.runeAt(0, 0).isEmpty());
        assertEquals(fire, preview.runeAt(0, 1).orElseThrow().symbolId());
    }
}
