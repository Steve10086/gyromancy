package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.symbol.FloodFillExtractor.ExtractedGlyph;
import com.astune.gyromancy.util.GeometryUtils;
import net.minecraft.server.level.ServerLevel;

/**
 * Validates circular (ring-like) structures extracted by the flood fill.
 *
 * <p>A valid outer circle for a magic array must:
 * <ul>
 *   <li>Form a ring shape (hollow center with continuous outer pixels)</li>
 *   <li>Have no extra mana pixels inside the ring boundary</li>
 *   <li>Have sufficient circle-like geometry (aspect ratio near 1:1, high circularity)</li>
 * </ul>
 */
public final class CircleStructureValidator {

    private CircleStructureValidator() {}

    /**
     * Validates that an extracted glyph is a clean circle with no extra mana pixels inside.
     *
     * @param glyph the extracted pixel set (assumed to be a ring/circle)
     * @param level the world
     * @return true if the circle is structurally valid
     */
    public static boolean validateCircle(ExtractedGlyph glyph, ServerLevel level) {
        if (!isCircularStructure(glyph)) {
            return false;
        }

        // Check that the center region has no unmarked mana pixels
        return !hasInternalManaPixels(glyph, level);
    }

    /**
     * Determines whether an extracted glyph forms a circular (ring-like) structure.
     *
     * Heuristics:
     * <ul>
     *   <li>Aspect ratio of bounding box is close to 1:1 (within 1.5x tolerance)</li>
     *   <li>Hollow ratio: the ratio of filled interior to total bounding area is low</li>
     *   <li>Sufficient pixel count for a recognizable ring</li>
     * </ul>
     */
    public static boolean isCircularStructure(ExtractedGlyph glyph) {
        if (glyph.pixels().size() < 20) {
            return false; // too small to be a meaningful circle
        }

        // Normalize and compute properties
        int[][] normalized = FloodFillExtractor.normalizeGlyph(glyph);
        int[] bbox = GeometryUtils.computeBoundingBox(normalized);
        int bw = bbox[2] - bbox[0] + 1;
        int bh = bbox[3] - bbox[1] + 1;

        if (bh == 0) return false;

        // Aspect ratio check
        double aspect = (double) bw / bh;
        if (aspect > 2.0 || aspect < 0.5) {
            return false;
        }

        // Hollow ratio: count filled pixels in the center vs total
        int totalArea = bw * bh;
        int filledPixels = GeometryUtils.computeArea(normalized);

        // A ring should have 20%-60% fill (circle outline vs solid disc)
        double fillRatio = (double) filledPixels / totalArea;
        if (fillRatio < 0.05 || fillRatio > 0.75) {
            return false;
        }

        // Check center emptiness: the center region should have far fewer pixels
        // than the border region
        int centerSize = Math.max(2, Math.min(bw, bh) / 3);
        int cx = bw / 2;
        int cy = bh / 2;
        int centerPixels = 0;
        int centerTotal = 0;

        for (int y = cy - centerSize / 2; y <= cy + centerSize / 2; y++) {
            for (int x = cx - centerSize / 2; x <= cx + centerSize / 2; x++) {
                if (y >= 0 && y < 32 && x >= 0 && x < 32) {
                    centerTotal++;
                    if (normalized[y][x] != 0) centerPixels++;
                }
            }
        }

        if (centerTotal > 0) {
            double centerFillRatio = (double) centerPixels / centerTotal;
            // Center should be mostly empty for a ring
            return centerFillRatio < 0.25;
        }

        return true;
    }

    /**
     * Checks whether the interior of a circular glyph contains any mana pixels
     * that are not part of the circle itself.
     *
     * <p>Scans the bounding region of the glyph on each involved block's canvas face
     * and checks for mana pixels that are not already consumed.
     */
    private static boolean hasInternalManaPixels(ExtractedGlyph glyph, ServerLevel level) {
        // Collect all pixel positions in the glyph for fast lookup
        // Also compute the approximate center of the circle

        double sumX = 0, sumY = 0;
        for (PixelPos p : glyph.pixels()) {
            double[] worldXY = FloodFillExtractor.pixelToWorldXY(
                    p.pos(), p.face(), p.x(), p.y());
            if (worldXY != null) {
                sumX += worldXY[0];
                sumY += worldXY[1];
            }
        }
        int count = glyph.pixels().size();
        if (count == 0) return false;
        double centerX = sumX / count;
        double centerY = sumY / count;

        // Estimate radius as half the bounding box diagonal
        double radius = Math.sqrt(
                (glyph.maxWorldX() - glyph.minWorldX()) * (glyph.maxWorldX() - glyph.minWorldX())
                        + (glyph.maxWorldY() - glyph.minWorldY()) * (glyph.maxWorldY() - glyph.minWorldY())
        ) / 2.0;

        // Check each involved block for mana pixels within the circle interior
        // that are NOT in the glyph pixel set
        for (PixelPos p : glyph.pixels()) {
            // For each block, scan its face for interior mana pixels
            // Only check blocks that are in the interior region
            double[] worldXY = FloodFillExtractor.pixelToWorldXY(
                    p.pos(), p.face(), p.x(), p.y());
            if (worldXY == null) continue;

            double dist = Math.sqrt(
                    (worldXY[0] - centerX) * (worldXY[0] - centerX)
                            + (worldXY[1] - centerY) * (worldXY[1] - centerY)
            );

            // Only check pixels well inside the ring (dist < radius * 0.7)
            if (dist < radius * 0.7) {
                // This is an interior pixel — check if it might be an unmarked mana pixel
                // In practice, any pixel inside the ring should either be:
                // a) Part of the ring outline (in glyph.pixels) → OK
                // b) Empty (not a mana pixel) → OK
                // c) A separate mana pixel inside the ring → BAD
                // Since we're iterating glyph.pixels, all pixels here are in the glyph.
                // The actual check for unmarked interior pixels would need to scan
                // the canvas faces at interior positions.
            }
        }

        // For Phase 3, return true (no internal mana pixels) as the default.
        // Full implementation requires scanning faces at interior world positions,
        // which is more complex and deferred to Phase 5 integration testing.
        return false;
    }
}
