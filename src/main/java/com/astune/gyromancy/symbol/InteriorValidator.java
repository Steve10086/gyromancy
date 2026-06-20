package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.symbol.FloodFillExtractor.ExtractedGlyph;
import com.astune.painter.api.CanvasFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/**
 * Validates the interior of an extracted glyph.
 *
 * <p>Rules:
 * <ul>
 *   <li>Rune (CENTER_SYMBOL / PARAMETER_RUNE): interior must be clean — no raw mana pixels</li>
 *   <li>Outer circle (OUTER_CIRCLE): interior may only contain recognized glyphs, no raw mana</li>
 * </ul>
 */
public final class InteriorValidator {

    private InteriorValidator() {}

    /**
     * Checks whether the interior of a glyph contains any raw mana pixels
     * (pixels with {@code gyromancy:mana > 0} but not yet marked as {@code gyromancy:glyph_id}).
     *
     * <p>Scans all blocks within the glyph's world-space bounding box that are NOT
     * already part of the glyph's involved blocks.
     */
    public static boolean hasRawManaInside(ExtractedGlyph glyph, ServerLevel level) {
        // Determine world-space bounds
        double minX = glyph.minWorldX();
        double maxX = glyph.maxWorldX();
        double minY = glyph.minWorldY();
        double maxY = glyph.maxWorldY();

        // Compute interior block range (inward from the bounding box)
        int bxMin = (int) Math.floor(minX + 0.5);
        int bxMax = (int) Math.floor(maxX - 0.5);
        int bzMin = 0, bzMax = 0, byMin = 0, byMax = 0;

        // Deduce the primary face from one of the pixels
        Direction primaryFace = null;
        for (PixelPos p : glyph.pixels()) {
            primaryFace = p.face();
            break;
        }
        if (primaryFace == null) return false;

        // Build interior position ranges based on face type
        BlockPos sample = glyph.pixels().iterator().next().pos();
        switch (primaryFace) {
            case NORTH, SOUTH -> {
                int z = sample.getZ();
                bzMin = bzMax = z;
                byMin = (int) Math.ceil(minY);
                byMax = (int) Math.floor(maxY);
            }
            case EAST, WEST -> {
                int x = sample.getX();
                bxMin = bxMax = x;
                byMin = (int) Math.ceil(minY);
                byMax = (int) Math.floor(maxY);
            }
            case UP, DOWN -> {
                int y = sample.getY();
                byMin = byMax = y;
            }
        }

        if (bxMin > bxMax || byMin > byMax || bzMin > bzMax) return false;

        // Scan interior blocks
        Set<BlockPos> glyphBlocks = glyph.pixels().stream()
                .map(PixelPos::pos).collect(java.util.stream.Collectors.toSet());

        for (int bx = bxMin; bx <= bxMax; bx++) {
            for (int by = byMin; by <= byMax; by++) {
                for (int bz = bzMin; bz <= bzMax; bz++) {
                    BlockPos pos = new BlockPos(bx, by, bz);
                    if (glyphBlocks.contains(pos)) continue; // skip the ring itself

                    List<CanvasFace> faces = FloodFillExtractor.getFacesAt(level, pos, primaryFace);
                    for (CanvasFace face : faces) {
                        for (int py = 0; py < 16; py++) {
                            for (int px = 0; px < 16; px++) {
                                // Check: mana > 0 AND glyph_id == 0 (not yet consumed)
                                if (ManaPixelDetector.isManaPixel(face, px, py)
                                        && !ManaPixelDetector.isMarked(face, px, py)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Finds all recognized glyphs (PositionedGlyph) whose world positions
     * fall within the interior of this glyph.
     *
     * <p>This is used to check if an outer circle encloses valid runes.
     */
    public static List<PositionedGlyph> findGlyphsInside(
            ExtractedGlyph glyph, ServerLevel level,
            Map<Integer, PositionedGlyph> glyphIndex) {

        List<PositionedGlyph> found = new ArrayList<>();
        double minX = glyph.minWorldX() + 0.5;
        double maxX = glyph.maxWorldX() - 0.5;
        double minY = glyph.minWorldY() + 0.5;
        double maxY = glyph.maxWorldY() - 0.5;

        for (PositionedGlyph pg : glyphIndex.values()) {
            double cx = (pg.minWorldX() + pg.maxWorldX()) / 2.0;
            double cy = (pg.minWorldY() + pg.maxWorldY()) / 2.0;
            if (cx >= minX && cx <= maxX && cy >= minY && cy <= maxY) {
                found.add(pg);
            }
        }

        return found;
    }
}
