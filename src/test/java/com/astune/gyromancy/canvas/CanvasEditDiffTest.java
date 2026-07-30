package com.astune.gyromancy.canvas;

import com.astune.gyromancy.api.symbol.SymbolRole;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasEditDiffTest {

    @Test
    void compileRegionIsLimitedToChangedPixelAndItsEightNeighbors() {
        CanvasDocument before = CanvasDocument.blank(1, 1);
        int[] colors = before.colors();
        int[] effects = before.strokeEffects();
        int changed = 8 * 16 + 8;
        colors[changed] = 0xFF000000;
        effects[changed] = 1;
        CanvasDocument after = before.withRaster(colors, effects);

        CanvasEditDiff diff = CanvasEditDiff.between(before, after);

        assertTrue(diff.changed().get(changed));
        assertTrue(diff.compileRegion().get(7 * 16 + 7));
        assertTrue(diff.compileRegion().get(9 * 16 + 9));
        assertFalse(diff.compileRegion().get(6 * 16 + 8));
    }

    @Test
    void onlyGlyphsTouchingTheLocalCompileRegionAreInvalidated() {
        CanvasDocument before = CanvasDocument.blank(1, 1);
        int[] effects = before.strokeEffects();
        effects[5 * 16 + 5] = 1;
        CanvasDocument after = before.withRaster(before.colors(), effects);
        CanvasEditDiff diff = CanvasEditDiff.between(before, after);

        CanvasGlyph nearby = glyph(new int[]{4 * 16 + 4});
        CanvasGlyph distant = glyph(new int[]{12 * 16 + 12});

        assertTrue(diff.touches(nearby));
        assertFalse(diff.touches(distant));
    }

    private static CanvasGlyph glyph(int[] cells) {
        return new CanvasGlyph(
                UUID.randomUUID(),
                ResourceLocation.fromNamespaceAndPath("gyromancy", "fire"),
                1.0F,
                SymbolRole.PARAMETER_RUNE,
                1.0, 0.0, 0.1, 0.1,
                0.0, 1.0, 0.0, 1.0,
                cells);
    }
}
