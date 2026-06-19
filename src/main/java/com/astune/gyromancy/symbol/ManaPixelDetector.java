package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.painter.api.CanvasData;
import com.astune.painter.api.CanvasFace;
import com.astune.painter.api.IPixelMatrix;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects "mana pixels" — pixels drawn with magical/elemental ink
 * that should trigger symbol recognition.
 *
 * <p>Detection is based on Pigmentum CanvasFace effect layers.
 * When the GyromancyPaintProvider (Phase 4) draws with magical ink,
 * it writes {@link #MANA_EFFECT_KEY} to the effect layer at the pixel position.
 * This detector reads that effect layer to identify mana pixels.
 *
 * <p>Also tracks consumed pixels via {@link #GLYPH_ID_KEY} to prevent
 * re-detection of already-recognized symbols.
 */
public final class ManaPixelDetector {

    /** Effect layer key written by GyromancyPaintProvider for magical ink pixels */
    public static final String MANA_EFFECT_KEY = "gyromancy:mana";

    /** Effect layer key written by GlyphMarker to mark consumed pixels */
    public static final String GLYPH_ID_KEY = "gyromancy:glyph_id";

    /** Fallback: minimum alpha threshold for color-based mana detection */
    private static final int MIN_ALPHA = 0x80;

    private ManaPixelDetector() {}

    /**
     * Checks whether a pixel at the given CanvasFace coordinates is a mana pixel.
     * First checks the effect layer, then falls back to color-based detection.
     */
    public static boolean isManaPixel(CanvasFace face, int x, int y) {
        // Primary: check effect layer
        if (face.getEffectValue(MANA_EFFECT_KEY, x, y) > 0) {
            return true;
        }

        // Fallback: color-based heuristic — non-transparent pixel
        int color = face.pixels().getPixel(x, y);
        return isOpaqueColor(color);
    }

    /**
     * Checks if a pixel has already been consumed (marked as part of a recognized glyph).
     */
    public static boolean isMarked(CanvasFace face, int x, int y) {
        return face.getEffectValue(GLYPH_ID_KEY, x, y) > 0;
    }

    /**
     * Checks if a pixel color is sufficiently opaque to be considered drawn.
     */
    public static boolean isOpaqueColor(int argb) {
        int alpha = (argb >> 24) & 0xFF;
        return alpha >= MIN_ALPHA;
    }

    /**
     * Scans all faces in a CanvasData for mana pixel positions.
     *
     * @param level the world
     * @param pos   the block position
     * @param data  the canvas data to scan
     * @return list of mana pixel positions found
     */
    public static List<PixelPos> scanForMana(Level level, BlockPos pos, CanvasData data) {
        List<PixelPos> seeds = new ArrayList<>();

        for (CanvasFace face : data.faces()) {
            IPixelMatrix pixels = face.pixels();
            int w = pixels.getWidth();
            int h = pixels.getHeight();

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (isManaPixel(face, x, y) && !isMarked(face, x, y)) {
                        int color = pixels.getPixel(x, y);
                        seeds.add(new PixelPos(pos, face.primaryFace(), x, y, color));
                    }
                }
            }
        }

        return seeds;
    }

    /**
     * Scans a single CanvasFace for mana pixels.
     */
    public static List<PixelPos> scanFace(BlockPos pos, CanvasFace face) {
        List<PixelPos> seeds = new ArrayList<>();
        IPixelMatrix pixels = face.pixels();
        int w = pixels.getWidth();
        int h = pixels.getHeight();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (isManaPixel(face, x, y) && !isMarked(face, x, y)) {
                    int color = pixels.getPixel(x, y);
                    seeds.add(new PixelPos(pos, face.primaryFace(), x, y, color));
                }
            }
        }

        return seeds;
    }
}
