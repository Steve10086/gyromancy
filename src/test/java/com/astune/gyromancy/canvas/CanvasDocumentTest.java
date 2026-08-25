package com.astune.gyromancy.canvas;

import com.astune.gyromancy.api.symbol.SymbolRole;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasDocumentTest {

    @Test
    void rasterResolutionIsIndependentFromPhysicalSize() {
        CanvasDocument document = CanvasDocument.blank(3, 2);

        assertEquals(16, document.resolutionWidth());
        assertEquals(16, document.resolutionHeight());
        assertEquals(16 * 16, document.colors().length);
    }

    @Test
    void emptyStateTracksRasterColorsAndEffects() {
        CanvasDocument blank = CanvasDocument.blank(1, 1);
        assertTrue(blank.isEmpty());

        int[] colors = blank.colors();
        colors[0] = 0xFF112233;
        assertFalse(blank.withRaster(colors, blank.strokeEffects()).isEmpty());

        int[] effects = blank.strokeEffects();
        effects[0] = 1;
        assertFalse(blank.withRaster(blank.colors(), effects).isEmpty());
    }

    @Test
    void exportsColorsForBottomOriginPatternCoordinates() {
        CanvasDocument blank = CanvasDocument.blank(1, 1);
        int[] colors = blank.colors();
        colors[1 * 16 + 2] = 0xFF112233;
        colors[14 * 16 + 3] = 0xFF445566;
        CanvasDocument document = blank.withRaster(
                colors, blank.strokeEffects());

        int[] bottomToTop = document.colorsBottomToTop();

        assertEquals(0xFF112233, bottomToTop[14 * 16 + 2]);
        assertEquals(0xFF445566, bottomToTop[1 * 16 + 3]);
        assertEquals(0xFF112233, document.colorAt(2, 1));
        assertEquals(0xFF445566, document.colorAt(3, 14));
    }

    @Test
    void increasingScaleReplicatesBothRasterMatricesAndGlyphOwnership() {
        CanvasDocument blank = CanvasDocument.blank(1, 1);
        int[] colors = blank.colors();
        int[] effects = blank.strokeEffects();
        colors[2 * 16 + 3] = 0xFF112233;
        effects[2 * 16 + 3] = 7;
        CanvasGlyph glyph = glyph(new int[]{2 * 16 + 3});
        CanvasDocument source = blank.withRaster(colors, effects)
                .withCompileCache(List.of(glyph), List.of());

        CanvasDocument scaled = source.resample(2);

        for (int y = 4; y <= 5; y++) {
            for (int x = 6; x <= 7; x++) {
                assertEquals(0xFF112233, scaled.colorAt(x, y));
                assertEquals(7, scaled.strokeEffectAt(x, y));
            }
        }
        assertArrayEquals(new int[]{
                4 * 32 + 6, 4 * 32 + 7,
                5 * 32 + 6, 5 * 32 + 7
        }, scaled.glyphs().getFirst().cells());
    }

    @Test
    void rejectsResolutionAboveTwoHundredFiftySix() {
        CanvasDocument document = CanvasDocument.blank(2, 1);

        assertEquals(16, CanvasDocument.maxScale());
        assertThrows(IllegalArgumentException.class, () -> document.resample(17));
    }

    @Test
    void physicalUpgradePreservesRasterCoordinatesAndCompileCaches() {
        CanvasDocument blank = CanvasDocument.blank(1, 1);
        int[] colors = blank.colors();
        int[] effects = blank.strokeEffects();
        colors[2 * 16 + 3] = 0xFF112233;
        effects[2 * 16 + 3] = 7;
        CanvasGlyph glyph = glyph(new int[]{2 * 16 + 3});
        CanvasArrayRecord array = new CanvasArrayRecord(
                glyph.glyphUuid(), List.of(glyph.glyphUuid()),
                CanvasArrayRecord.fingerprint(glyph.glyphUuid(), List.of(glyph.glyphUuid())));
        CanvasDocument source = blank.withRaster(colors, effects)
                .withCompileCache(List.of(glyph), List.of(array));

        CanvasDocument upgraded = source.increasePhysicalSize();

        assertEquals(2, upgraded.physicalWidth());
        assertEquals(2, upgraded.physicalHeight());
        assertEquals(source.resolutionScale(), upgraded.resolutionScale());
        assertEquals(source.resolutionWidth(), upgraded.resolutionWidth());
        assertEquals(source.resolutionHeight(), upgraded.resolutionHeight());
        assertArrayEquals(source.colors(), upgraded.colors());
        assertArrayEquals(source.strokeEffects(), upgraded.strokeEffects());
        assertEquals(source.glyphs(), upgraded.glyphs());
        assertEquals(source.arrays(), upgraded.arrays());
    }

    @Test
    void physicalUpgradeStopsAtMaximumSize() {
        CanvasDocument maximum = CanvasDocument.blank(
                CanvasDocument.MAX_PHYSICAL_SIZE, CanvasDocument.MAX_PHYSICAL_SIZE);

        assertFalse(maximum.canIncreasePhysicalSize());
        assertThrows(IllegalStateException.class, maximum::increasePhysicalSize);
    }

    @Test
    void duplicateKeepsContentAtOneBlockWithIndependentGlyphIdentities() {
        CanvasDocument blank = CanvasDocument.blank(4, 3);
        int[] colors = blank.colors();
        int[] effects = blank.strokeEffects();
        colors[2 * 16 + 3] = 0xFF112233;
        effects[2 * 16 + 3] = 7;
        CanvasGlyph glyph = glyph(new int[]{2 * 16 + 3});
        CanvasArrayRecord array = new CanvasArrayRecord(
                glyph.glyphUuid(), List.of(glyph.glyphUuid()),
                CanvasArrayRecord.fingerprint(glyph.glyphUuid(), List.of(glyph.glyphUuid())));
        CanvasDocument source = blank.withRaster(colors, effects)
                .withCompileCache(List.of(glyph), List.of(array));

        CanvasDocument copy = source.duplicateAsSingleBlock();

        assertEquals(1, copy.physicalWidth());
        assertEquals(1, copy.physicalHeight());
        assertEquals(source.resolutionScale(), copy.resolutionScale());
        assertArrayEquals(source.colors(), copy.colors());
        assertArrayEquals(source.strokeEffects(), copy.strokeEffects());
        assertNotEquals(source.glyphs().getFirst().glyphUuid(), copy.glyphs().getFirst().glyphUuid());
        assertEquals(copy.glyphs().getFirst().glyphUuid(), copy.arrays().getFirst().rootGlyph());
        assertEquals(copy.arrays().getFirst().boundGlyphs(),
                List.of(copy.glyphs().getFirst().glyphUuid()));
    }

    @Test
    void codecRoundTripPreservesCompileCaches() {
        UUID root = UUID.fromString("00000000-0000-0000-0000-000000000111");
        CanvasGlyph glyph = new CanvasGlyph(
                root,
                ResourceLocation.fromNamespaceAndPath("gyromancy", "circle_outer"),
                0.9F,
                SymbolRole.OUTER_CIRCLE,
                1.0, 0.0, 0.5, 0.5,
                0.1, 0.9, 0.1, 0.9,
                new int[]{1, 2, 3});
        CanvasArrayRecord array = new CanvasArrayRecord(
                root, List.of(root), CanvasArrayRecord.fingerprint(root, List.of(root)),
                0xFF5A7BC1);
        CanvasDocument source = CanvasDocument.blank(1, 1)
                .withCompileCache(List.of(glyph), List.of(array));

        var encoded = CanvasDocument.CODEC.encodeStart(JsonOps.INSTANCE, source).getOrThrow();
        CanvasDocument decoded = CanvasDocument.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(source, decoded);
        assertTrue(decoded.arrays().getFirst().fingerprint() != 0);
        assertEquals(0xFF5A7BC1, decoded.arrays().getFirst().color());
    }

    private static CanvasGlyph glyph(int[] cells) {
        return new CanvasGlyph(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                ResourceLocation.fromNamespaceAndPath("gyromancy", "fire"),
                1.0F,
                SymbolRole.PARAMETER_RUNE,
                1.0, 0.0, 0.1, 0.1,
                0.1, 0.2, 0.1, 0.2,
                cells);
    }
}
