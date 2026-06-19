package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.symbol.FloodFillExtractor.ExtractedGlyph;
import com.astune.painter.api.CanvasFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;

/**
 * Marks matched glyph pixels as "consumed" in CanvasFace effect layers
 * to prevent re-detection of the same symbol.
 *
 * <p>Uses Pigmentum's {@code CanvasFace.setEffectValue()} to write a glyph
 * identifier to each consumed pixel. Subsequent {@link ManaPixelDetector}
 * checks will skip marked pixels.
 */
public final class GlyphMarker {

    /** Next glyph ID counter (incremented per glyph) */
    private static int nextGlyphId = 1;

    private GlyphMarker() {}

    /**
     * Marks all pixels in an extracted glyph as consumed.
     * Writes the glyph ID to each pixel's effect layer.
     *
     * @param glyph    the extracted pixel set
     * @param glyphId  ID to assign (auto-generated if <= 0)
     * @param level    the world
     */
    public static void markConsumed(ExtractedGlyph glyph, int glyphId, ServerLevel level) {
        int id = glyphId > 0 ? glyphId : nextGlyphId++;

        // Group pixels by (BlockPos, Direction) for efficient batch writing
        Map<BlockPos, Map<Direction, java.util.List<PixelPos>>> grouped = new HashMap<>();

        for (PixelPos p : glyph.pixels()) {
            grouped
                    .computeIfAbsent(p.pos(), k -> new HashMap<>())
                    .computeIfAbsent(p.face(), k -> new java.util.ArrayList<>())
                    .add(p);
        }

        for (var blockEntry : grouped.entrySet()) {
            BlockPos pos = blockEntry.getKey();
            if (!level.isLoaded(pos)) continue;

            for (var faceEntry : blockEntry.getValue().entrySet()) {
                Direction dir = faceEntry.getKey();
                CanvasFace face = FloodFillExtractor.getFaceAt(level, pos, dir);
                if (face == null) continue;

                for (PixelPos p : faceEntry.getValue()) {
                    face.setEffectValue(ManaPixelDetector.GLYPH_ID_KEY, p.x(), p.y(), id);
                }
            }
        }
    }

    /**
     * Convenience method using auto-generated glyph ID.
     */
    public static void markConsumed(ExtractedGlyph glyph, ServerLevel level) {
        markConsumed(glyph, 0, level);
    }

    /**
     * Clears glyph markings from all pixels in a glyph.
     * Used when a magic array is destroyed.
     */
    public static void clearMarks(ExtractedGlyph glyph, ServerLevel level) {
        // Write 0 to all glyph ID effect values for the glyph's pixels
        Map<BlockPos, Map<Direction, java.util.List<PixelPos>>> grouped = new HashMap<>();

        for (PixelPos p : glyph.pixels()) {
            grouped
                    .computeIfAbsent(p.pos(), k -> new HashMap<>())
                    .computeIfAbsent(p.face(), k -> new java.util.ArrayList<>())
                    .add(p);
        }

        for (var blockEntry : grouped.entrySet()) {
            BlockPos pos = blockEntry.getKey();
            if (!level.isLoaded(pos)) continue;

            for (var faceEntry : blockEntry.getValue().entrySet()) {
                Direction dir = faceEntry.getKey();
                CanvasFace face = FloodFillExtractor.getFaceAt(level, pos, dir);
                if (face == null) continue;

                for (PixelPos p : faceEntry.getValue()) {
                    face.setEffectValue(ManaPixelDetector.GLYPH_ID_KEY, p.x(), p.y(), 0);
                }
            }
        }
    }

    /** Returns and increments the next glyph ID */
    public static int nextGlyphId() {
        return nextGlyphId++;
    }
}
