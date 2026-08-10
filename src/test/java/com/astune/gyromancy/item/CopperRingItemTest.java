package com.astune.gyromancy.item;

import com.astune.gyromancy.api.canvas.Carvable;
import com.astune.gyromancy.canvas.CanvasDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopperRingItemTest {
    @Test
    void copperRingIsCarvableAndOwnsA64By64Canvas() {
        CanvasDocument canvas = CopperRingItem.blankCanvas();

        assertTrue(Carvable.class.isAssignableFrom(CopperRingItem.class));
        assertEquals(64, canvas.resolutionWidth());
        assertEquals(64, canvas.resolutionHeight());
        assertEquals(64 * 64, canvas.colors().length);
        assertEquals(64 * 64, canvas.strokeEffects().length);
    }

    @Test
    void onlyTheCopperRingSurfaceIsCarvable() {
        assertTrue(CopperRingItem.isCarvingPixel(32, 12));
        assertFalse(CopperRingItem.isCarvingPixel(32, 32));
        assertFalse(CopperRingItem.isCarvingPixel(0, 0));
    }
}
