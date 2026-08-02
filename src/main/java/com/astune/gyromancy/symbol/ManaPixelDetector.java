package com.astune.gyromancy.symbol;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.painter.api.CanvasData;
import com.astune.painter.api.CanvasFace;
import com.astune.painter.api.IPixelMatrix;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects mana pixels drawn with magical ink.
 *
 * <p>Detection is based strictly on Pigmentum CanvasFace effect layers. Ordinary
 * non-transparent paint must not enter symbol recognition unless it carries
 * {@link #MANA_EFFECT_KEY}.
 */
public final class ManaPixelDetector {

    /** Effect layer key written by magical ink pixels. */
    public static final String MANA_EFFECT_KEY = "gyromancy:mana";

    /** Effect layer key written by GlyphMarker to mark consumed pixels. */
    public static final String GLYPH_ID_KEY = "gyromancy:glyph_id";

    /** Effect layer key written by GlyphMarker to store the symbol registry id + 1. */
    public static final String SYMBOL_ID_KEY = "gyromancy:symbol_id";

    private ManaPixelDetector() {}

    /**
     * Checks whether a pixel at the given CanvasFace coordinates is a mana pixel.
     */
    public static boolean isManaPixel(CanvasFace face, int x, int y) {
        return face.getEffectValue(MANA_EFFECT_KEY, x, y) > 0;
    }

    /**
     * Checks if a pixel has already been consumed (marked as part of a recognized glyph).
     */
    public static boolean isMarked(CanvasFace face, int x, int y) {
        return face.getEffectValue(GLYPH_ID_KEY, x, y) > 0;
    }

    /**
     * Returns whether a pixel may start or participate in a new flood fill.
     * The symbol layer is descriptive metadata and may be refreshed independently,
     * so only glyph_id decides whether existing mana is already owned.
     */
    public static boolean isUnclaimedManaPixel(CanvasFace face, int x, int y) {
        return isUnclaimedMana(
                face.getEffectValue(MANA_EFFECT_KEY, x, y),
                face.getEffectValue(GLYPH_ID_KEY, x, y));
    }

    static boolean isUnclaimedMana(int mana, int glyphId) {
        return mana > 0 && glyphId == 0;
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
                    if (isUnclaimedManaPixel(face, x, y)) {
                        int color = pixels.getPixel(x, y);
                        PixelPos seed = new PixelPos(pos, face.primaryFace(), x, y, color);
                        seeds.add(seed);
                        Gyromancy.LOGGER.debug("[ManaSeed] + seed ({},{}) face={} pos={} color={}",
                                x, y, face.primaryFace(), pos, String.format("0x%08X", color));
                    }
                }
            }
        }

        if (seeds.isEmpty()) {
            Gyromancy.LOGGER.debug("[ManaSeed] no seeds at {} ({} faces scanned)", pos, data.faces().size());
        } else {
            Gyromancy.LOGGER.debug("[ManaSeed] {} seed(s) total at {}", seeds.size(), pos);
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
                if (isUnclaimedManaPixel(face, x, y)) {
                    int color = pixels.getPixel(x, y);
                    seeds.add(new PixelPos(pos, face.primaryFace(), x, y, color));
                }
            }
        }

        return seeds;
    }
}
