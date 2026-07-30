package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.array.MagicArrayManager;
import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.canvas.CanvasEntity;
import com.astune.gyromancy.registry.ModSymbols;
import com.astune.painter.api.CanvasFace;
import net.minecraft.server.level.ServerLevel;

/**
 * Verifies that a persisted glyph still owns the canvas stroke it describes.
 *
 * <p>Checking the glyph id as well as the symbol id is important: stale
 * persisted glyph records can otherwise make one marked stroke appear to be
 * several different runes during circle collection.
 */
public final class GlyphStrokeValidator {
    private GlyphStrokeValidator() {}

    public static boolean isValidForCollection(PositionedGlyph glyph,
                                               MagicArrayManager manager,
                                               ServerLevel level) {
        PositionedGlyph indexed = manager.getGlyph(glyph.glyphId());
        return indexed != null
                && indexed.glyphUuid().equals(glyph.glyphUuid())
                && isValid(glyph, level);
    }

    public static boolean isValid(PositionedGlyph glyph, ServerLevel level) {
        if (glyph.sourceCanvasId().isPresent()) {
            return level.getEntity(glyph.sourceCanvasId().get()) instanceof CanvasEntity canvas
                    && canvas.containsGlyph(glyph.glyphUuid());
        }
        if (glyph.pixels().isEmpty()) return false;

        int expectedSymbolId = ModSymbols.symbolLayerValueFor(glyph.symbolId());
        if (expectedSymbolId <= 0) return false;

        for (PixelPos pixel : glyph.pixels()) {
            if (!level.isLoaded(pixel.pos())) return false;

            CanvasFace face = FloodFillExtractor.getFaceAt(level, pixel.pos(), pixel.face());
            if (face == null
                    || pixel.x() < 0 || pixel.x() >= face.pixels().getWidth()
                    || pixel.y() < 0 || pixel.y() >= face.pixels().getHeight()) {
                return false;
            }

            if (!isExpectedMarker(
                    face.getEffectValue(ManaPixelDetector.MANA_EFFECT_KEY, pixel.x(), pixel.y()),
                    face.getEffectValue(ManaPixelDetector.GLYPH_ID_KEY, pixel.x(), pixel.y()),
                    face.getEffectValue(ManaPixelDetector.SYMBOL_ID_KEY, pixel.x(), pixel.y()),
                    glyph.glyphId(),
                    expectedSymbolId)) {
                return false;
            }
        }
        return true;
    }

    static boolean isExpectedMarker(int mana, int markedGlyphId, int markedSymbolId,
                                    int expectedGlyphId, int expectedSymbolId) {
        return (mana & 0xFF) > 0
                && (markedGlyphId & 0xFF) == (expectedGlyphId & 0xFF)
                && (markedSymbolId & 0xFF) == (expectedSymbolId & 0xFF);
    }
}
