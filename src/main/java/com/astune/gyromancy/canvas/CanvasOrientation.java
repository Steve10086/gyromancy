package com.astune.gyromancy.canvas;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Defines the two in-plane axes for every possible support face.
 *
 * <p>Width x height always forms the outward face normal. This gives floor,
 * ceiling, and wall canvases the same local coordinate contract.
 */
public final class CanvasOrientation {
    private CanvasOrientation() {}

    public static Vec3 widthAxis(Direction normal) {
        if (normal.getAxis().isHorizontal()) {
            return Vec3.atLowerCornerOf(normal.getCounterClockWise().getNormal());
        }
        return Vec3.atLowerCornerOf(Direction.EAST.getNormal());
    }

    public static Vec3 heightAxis(Direction normal) {
        return switch (normal) {
            case UP -> Vec3.atLowerCornerOf(Direction.NORTH.getNormal());
            case DOWN -> Vec3.atLowerCornerOf(Direction.SOUTH.getNormal());
            default -> Vec3.atLowerCornerOf(Direction.UP.getNormal());
        };
    }
}
