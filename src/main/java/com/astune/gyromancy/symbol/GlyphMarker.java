package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.symbol.FloodFillExtractor.ExtractedGlyph;
import com.astune.painter.api.CanvasFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Marks matched glyph pixels as consumed in CanvasFace effect layers.
 *
 * <p>The glyph_id effect value is a unique glyph instance id. The symbol_id
 * effect value is the symbol registry id + 1 and is used by
 * {@link ManaPixelDetector#isMarked}.
 */
public final class GlyphMarker {

    private GlyphMarker() {}

    public static void markConsumed(ExtractedGlyph glyph, int glyphId, int symbolLayerValue, ServerLevel level) {
        Map<BlockPos, Map<Direction, java.util.List<PixelPos>>> grouped = groupByFace(glyph);
        markConsumed(grouped, glyphId, symbolLayerValue, level);
    }

    public static void markConsumed(PositionedGlyph glyph, int symbolLayerValue, ServerLevel level) {
        markConsumed(groupByFace(glyph.pixels()), glyph.glyphId(), symbolLayerValue, level);
    }

    private static void markConsumed(Map<BlockPos, Map<Direction, java.util.List<PixelPos>>> grouped,
                                     int glyphId, int symbolLayerValue, ServerLevel level) {

        for (var blockEntry : grouped.entrySet()) {
            BlockPos pos = blockEntry.getKey();
            if (!level.isLoaded(pos)) continue;

            for (var faceEntry : blockEntry.getValue().entrySet()) {
                CanvasFace face = FloodFillExtractor.getFaceAt(level, pos, faceEntry.getKey());
                if (face == null) continue;

                for (PixelPos p : faceEntry.getValue()) {
                    face.setEffectValue(ManaPixelDetector.GLYPH_ID_KEY, p.x(), p.y(), glyphId);
                    face.setEffectValue(ManaPixelDetector.SYMBOL_ID_KEY, p.x(), p.y(), symbolLayerValue);
                }
            }
        }
    }

    public static void clearMarks(ExtractedGlyph glyph, ServerLevel level) {
        clearMarks(glyph.pixels(), level);
    }

    public static void clearMarks(PositionedGlyph glyph, ServerLevel level) {
        clearMarks(glyph.pixels(), level);
    }

    private static void clearMarks(Set<PixelPos> pixels, ServerLevel level) {
        Map<BlockPos, Map<Direction, java.util.List<PixelPos>>> grouped = groupByFace(pixels);

        for (var blockEntry : grouped.entrySet()) {
            BlockPos pos = blockEntry.getKey();
            if (!level.isLoaded(pos)) continue;

            for (var faceEntry : blockEntry.getValue().entrySet()) {
                CanvasFace face = FloodFillExtractor.getFaceAt(level, pos, faceEntry.getKey());
                if (face == null) continue;

                for (PixelPos p : faceEntry.getValue()) {
                    face.setEffectValue(ManaPixelDetector.GLYPH_ID_KEY, p.x(), p.y(), 0);
                    face.setEffectValue(ManaPixelDetector.SYMBOL_ID_KEY, p.x(), p.y(), 0);
                }
            }
        }
    }

    private static Map<BlockPos, Map<Direction, java.util.List<PixelPos>>> groupByFace(ExtractedGlyph glyph) {
        return groupByFace(glyph.pixels());
    }

    private static Map<BlockPos, Map<Direction, java.util.List<PixelPos>>> groupByFace(Set<PixelPos> pixels) {
        Map<BlockPos, Map<Direction, java.util.List<PixelPos>>> grouped = new HashMap<>();
        for (PixelPos p : pixels) {
            grouped
                    .computeIfAbsent(p.pos(), k -> new HashMap<>())
                    .computeIfAbsent(p.face(), k -> new java.util.ArrayList<>())
                    .add(p);
        }
        return grouped;
    }
}
