package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.symbol.FloodFillExtractor.ExtractedGlyph;
import com.astune.painter.api.CanvasFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Validates the interior of an extracted glyph.
 *
 * <p>Rules:
 * <ul>
 *   <li>Center symbol / rune: interior must be clean — no raw mana pixels</li>
 *   <li>Outer circle (OUTER_CIRCLE): interior may only contain recognized glyphs, no raw mana</li>
 * </ul>
 */
public final class InteriorValidator {

    private InteriorValidator() {}

    /**
     * Checks whether the interior of a glyph contains any raw mana pixels
     * (pixels with {@code gyromancy:mana > 0} but not yet marked as {@code gyromancy:symbol_id}).
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
                                // Check: mana > 0 AND symbol_id == 0 (not yet consumed)
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
            Map<?, PositionedGlyph> glyphIndex) {

        List<PositionedGlyph> found = new ArrayList<>();
        Box interior = interiorBox(glyph, level);
        if (interior == null || interior.empty()) return found;

        for (PositionedGlyph pg : glyphIndex.values()) {
            Vec3 center = glyphCenter(pg, level);
            if (center != null && interior.contains(center.x, center.y, center.z)) {
                found.add(pg);
            }
        }

        return found;
    }

    private static Box interiorBox(ExtractedGlyph glyph, ServerLevel level) {
        Vec3 center = glyphCenter(glyph, level);
        if (center == null) return null;

        double minU = glyph.minWorldX() + 0.5;
        double maxU = glyph.maxWorldX() - 0.5;
        double minV = glyph.minWorldY() + 0.5;
        double maxV = glyph.maxWorldY() - 0.5;
        Axis axis = planeAxis(glyph.pixels().iterator().next().face());
        double plane = coordinate(center, axis);
        double minN = plane - 1.0 / 32.0;
        double maxN = plane + 1.0 / 32.0;

        return switch (axis) {
            case X -> new Box(minN, maxN, minV, maxV, minU, maxU);
            case Y -> new Box(minU, maxU, minN, maxN, minV, maxV);
            case Z -> new Box(minU, maxU, minV, maxV, minN, maxN);
        };
    }

    private static Vec3 glyphCenter(ExtractedGlyph glyph, ServerLevel level) {
        return FloodFillExtractor.worldCenter(level, glyph.pixels(),
                        glyph.minWorldX(), glyph.maxWorldX(), glyph.minWorldY(), glyph.maxWorldY())
                .orElseGet(() -> fallbackCenter(glyph.pixels(),
                        glyph.minWorldX(), glyph.maxWorldX(), glyph.minWorldY(), glyph.maxWorldY()));
    }

    private static Vec3 glyphCenter(PositionedGlyph glyph, ServerLevel level) {
        return FloodFillExtractor.worldCenter(level, glyph.pixels(),
                        glyph.minWorldX(), glyph.maxWorldX(), glyph.minWorldY(), glyph.maxWorldY())
                .orElseGet(() -> fallbackCenter(glyph.pixels(),
                        glyph.minWorldX(), glyph.maxWorldX(), glyph.minWorldY(), glyph.maxWorldY()));
    }

    private static Vec3 fallbackCenter(Set<PixelPos> pixels,
                                       double minWorldX, double maxWorldX,
                                       double minWorldY, double maxWorldY) {
        PixelPos sample = pixels.stream().findAny().orElse(null);
        if (sample == null) return null;

        double a = (minWorldX + maxWorldX) * 0.5;
        double b = (minWorldY + maxWorldY) * 0.5;
        Vec3 sampleWorld = switch (sample.face()) {
            case NORTH, SOUTH -> new Vec3(0.0, 0.0, surfaceCoordinate(sample.pos(), sample.face()));
            case EAST, WEST -> new Vec3(surfaceCoordinate(sample.pos(), sample.face()), 0.0, 0.0);
            case UP, DOWN -> new Vec3(0.0, surfaceCoordinate(sample.pos(), sample.face()), 0.0);
        };
        return FloodFillExtractor.unflatten(sample.face(), sampleWorld, a, b);
    }

    private static double surfaceCoordinate(BlockPos pos, Direction face) {
        return switch (face) {
            case NORTH -> pos.getZ();
            case SOUTH -> pos.getZ() + 1.0;
            case WEST -> pos.getX();
            case EAST -> pos.getX() + 1.0;
            case DOWN -> pos.getY();
            case UP -> pos.getY() + 1.0;
        };
    }

    private static double coordinate(Vec3 vec, Axis axis) {
        return switch (axis) {
            case X -> vec.x;
            case Y -> vec.y;
            case Z -> vec.z;
        };
    }

    private static Axis planeAxis(Direction face) {
        return switch (face) {
            case NORTH, SOUTH -> Axis.Z;
            case EAST, WEST -> Axis.X;
            case UP, DOWN -> Axis.Y;
        };
    }

    private enum Axis { X, Y, Z }

    private record Box(double minX, double maxX, double minY, double maxY, double minZ, double maxZ) {
        boolean empty() {
            return minX > maxX || minY > maxY || minZ > maxZ;
        }

        boolean contains(double x, double y, double z) {
            return x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }
    }
}
