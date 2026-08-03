package com.astune.gyromancy.wand;

import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.canvas.CanvasGlyph;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WandSlotCompilerTest {
    @Test
    void combinesCenteredStrokesAtTheirPhysicalCanvasScale() {
        int[] smallColors = new int[16 * 16];
        int[] smallEffects = new int[16 * 16];
        Arrays.fill(smallColors, 0xFFFF0000);
        Arrays.fill(smallEffects, 7);
        CanvasDocument small = new CanvasDocument(
                1, 1, 1, smallColors, smallEffects, List.of(), List.of());

        int[] largeColors = new int[16 * 16];
        Arrays.fill(largeColors, 0xFF0000FF);
        CanvasDocument largeWithoutStrokes = new CanvasDocument(
                2, 1, 1, largeColors, new int[16 * 16], List.of(), List.of());

        CanvasDocument combined = WandSlotCompiler.combineDocuments(
                List.of(small, largeWithoutStrokes)).orElseThrow();

        assertEquals(2, combined.physicalWidth());
        assertEquals(1, combined.physicalHeight());
        assertEquals(2, combined.resolutionScale());
        assertEquals(0, combined.strokeEffectAt(7, 16));
        assertEquals(7, combined.strokeEffectAt(8, 16));
        assertEquals(0xFFFF0000, combined.colorAt(8, 16));
        assertEquals(7, combined.strokeEffectAt(23, 16));
        assertEquals(0, combined.strokeEffectAt(24, 16));
        assertEquals(0, combined.colorAt(24, 16),
                "material pixels without strokes must not leak into the wand snapshot");
    }

    @Test
    void unchangedCombinedRasterKeepsThePersistedCompileCache() {
        int[] sourceColors = new int[16 * 16];
        int[] sourceEffects = new int[16 * 16];
        sourceColors[0] = 0xFFFF0000;
        sourceEffects[0] = 1;
        CanvasDocument raster = new CanvasDocument(
                1, 1, 1, sourceColors, sourceEffects, List.of(), List.of());
        CanvasGlyph cachedGlyph = new CanvasGlyph(
                UUID.randomUUID(),
                ResourceLocation.fromNamespaceAndPath("gyromancy", "fire"),
                1.0F, SymbolRole.CENTER_SYMBOL,
                0.0, -1.0, 1.0, 1.0,
                0.25, 0.75, 0.25, 0.75, new int[0]);
        int[] displayColors = new int[16 * 16];
        displayColors[0] = 0x80123456;
        CanvasDocument cached = raster.withCompileCache(List.of(cachedGlyph), List.of())
                .withRaster(displayColors, sourceEffects);
        WandSlotSnapshot previous = WandSlotSnapshot.of(
                cached, WandSlotCompiler.rasterFingerprint(raster));

        WandSlotSnapshot refreshed = WandSlotCompiler.refreshSnapshot(
                previous, Optional.of(raster));

        assertSame(previous, refreshed);
        assertSame(cached, refreshed.document().orElseThrow());
    }

    @Test
    void slotSnapshotsPersistTheirCombinedDocumentAndCompileCache() {
        WandSlotSnapshots snapshots = new WandSlotSnapshots(List.of(
                WandSlotSnapshot.of(CanvasDocument.blank(2, 1)),
                WandSlotSnapshot.EMPTY));

        var encoded = WandSlotSnapshots.CODEC
                .encodeStart(JsonOps.INSTANCE, snapshots).getOrThrow();
        WandSlotSnapshots decoded = WandSlotSnapshots.CODEC
                .parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(snapshots, decoded);
        assertTrue(decoded.get(1).document().isEmpty());
    }
}
