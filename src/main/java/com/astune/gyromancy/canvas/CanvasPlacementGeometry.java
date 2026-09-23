package com.astune.gyromancy.canvas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Pure placement geometry for hanging canvases.
 *
 * <p>The anchor block always holds the canvas' bottom-left corner as seen by
 * the placer, so the canvas only extends towards their right and away from
 * them. A ceiling document faces down, which puts its +U axis on the placer's
 * left and the anchor on the positive side of that axis.
 */
public final class CanvasPlacementGeometry {
    /** Rendered thickness of the canvas plane, in blocks. */
    public static final float DEPTH = 1.0F / 16.0F;

    private CanvasPlacementGeometry() {}

    /** The active canvas bounds for a placement anchored at {@code pos}. */
    public static AABB unfurledBounds(BlockPos pos, Direction facing, Direction orientation,
                                      int width, int height) {
        Vec3 widthAxis = CanvasOrientation.rightAxis(orientation, facing);
        Vec3 heightAxis = CanvasOrientation.topAxis(orientation);
        Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 base = Vec3.atCenterOf(pos).relative(facing, -0.46875);
        boolean anchorOnPositiveU = CanvasOrientation.anchorOnPositiveU(facing);
        Vec3 anchorCorner = base
                .add(widthAxis.scale(anchorOnPositiveU ? 0.5 : -0.5))
                .subtract(heightAxis.scale(0.5));
        double growth = anchorOnPositiveU ? -1.0 : 1.0;
        Vec3 center = anchorCorner
                .add(widthAxis.scale(growth * width * 0.5))
                .add(heightAxis.scale(height * 0.5));
        double sizeX = Math.abs(widthAxis.x) * width
                + Math.abs(heightAxis.x) * height + Math.abs(normal.x) * DEPTH;
        double sizeY = Math.abs(widthAxis.y) * width
                + Math.abs(heightAxis.y) * height + Math.abs(normal.y) * DEPTH;
        double sizeZ = Math.abs(widthAxis.z) * width
                + Math.abs(heightAxis.z) * height + Math.abs(normal.z) * DEPTH;
        return AABB.ofSize(center, sizeX, sizeY, sizeZ);
    }
}
