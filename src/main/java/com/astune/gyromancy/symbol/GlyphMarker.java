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
     * Color indices encoded into the glyph_id effect layer (upper 2 bits).
     * Decoded on the client by {@code GlyphImageProvider} to determine pixel color.
     */
    public static final int COLOR_FIRE  = 1; // → Red   0xFFFF0000
    public static final int COLOR_WATER = 2; // → Blue  0xFF0000FF
    public static final int COLOR_EARTH = 3; // → Brown 0xFF8B4513

    /**
     * Encodes color index + glyph sequence into a single byte for the effect layer.
     * Upper 2 bits = color, lower 6 bits = per-color sequence (0-63).
     */
    public static int encodeGlyphValue(int colorIndex, int sequence) {
        return ((colorIndex & 0x3) << 6) | (sequence & 0x3F);
    }

    /** Extract color index from encoded glyph value. */
    public static int decodeColorIndex(int encoded) { return (encoded >> 6) & 0x3; }

    /**
     * Marks all pixels in an extracted glyph as consumed, encoding both
     * the color index and sequence number into the glyph_id effect layer.
     *
     * @param glyph      the extracted pixel set
     * @param colorIndex color index (1=fire/red, 2=water/blue, 3=earth/brown)
     * @param sequence   per-color sequence number (auto-generated if <= 0)
     * @param level      the world
     */
    public static void markConsumed(ExtractedGlyph glyph, int colorIndex, int sequence, ServerLevel level) {
        int seq = sequence > 0 ? sequence : nextGlyphId++;
        int encoded = encodeGlyphValue(colorIndex, seq);

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
                    face.setEffectValue(ManaPixelDetector.GLYPH_ID_KEY, p.x(), p.y(), encoded);
                }
            }
        }
    }

    /**
     * Convenience: auto-generates both sequence and a default color index (1=fire).
     * Prefer {@link #markConsumed(ExtractedGlyph, int, int, ServerLevel)} with an explicit color.
     */
    public static void markConsumed(ExtractedGlyph glyph, ServerLevel level) {
        markConsumed(glyph, COLOR_FIRE, 0, level);
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
