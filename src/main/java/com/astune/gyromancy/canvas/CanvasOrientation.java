package com.astune.gyromancy.canvas;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Defines the two in-plane axes for every possible support face.
 *
 * <p>The document's local +V axis is the direction its top edge points at.
 * Wall canvases keep it at world-up; floor and ceiling canvases derive it from
 * the placer's horizontal facing, so the bottom edge always faces the placer.
 * The local +U axis follows {@code u x v = normal}, which keeps the document
 * readable from the side the canvas was placed on.
 */
public final class CanvasOrientation {
    private CanvasOrientation() {}

    /** The in-plane top direction used when no placement context is known. */
    public static Direction defaultTop(Direction normal) {
        return switch (normal) {
            case UP -> Direction.NORTH;
            case DOWN -> Direction.SOUTH;
            default -> Direction.UP;
        };
    }

    /** Returns {@code candidate} when it lies in the plane, otherwise the face default. */
    public static Direction compatibleTop(Direction normal, @Nullable Direction candidate) {
        return candidate != null && candidate.getAxis() != normal.getAxis()
                ? candidate : defaultTop(normal);
    }

    /** The document's +V direction for a canvas placed by a viewer facing {@code viewerFacing}. */
    public static Direction topDirection(Direction normal, Direction viewerFacing) {
        if (normal.getAxis().isHorizontal()) return Direction.UP;
        return viewerFacing.getAxis().isHorizontal()
                ? viewerFacing : defaultTop(normal);
    }

    /**
     * Whether the anchor block sits on the canvas' +U side. A ceiling document
     * faces down, so its +U axis points to the viewer's left and the anchor
     * (their bottom-left corner) lands on the positive side.
     */
    public static boolean anchorOnPositiveU(Direction normal) {
        return normal == Direction.DOWN;
    }

    public static Vec3 topAxis(Direction top) {
        return Vec3.atLowerCornerOf(top.getNormal());
    }

    /** The in-plane width axis, derived from {@code u x v = normal}. */
    public static Vec3 rightAxis(Direction top, Direction normal) {
        // Integer cross product keeps axis components exact (no negative zero).
        return Vec3.atLowerCornerOf(top.getNormal().cross(normal.getNormal()));
    }

    /** Legacy helper: the width axis of a face without placement context. */
    public static Vec3 widthAxis(Direction normal) {
        return rightAxis(defaultTop(normal), normal);
    }

    /** Legacy helper: the height axis of a face without placement context. */
    public static Vec3 heightAxis(Direction normal) {
        return topAxis(defaultTop(normal));
    }

    /** The horizontal direction from {@code from} towards {@code to}, or null when degenerate. */
    @Nullable
    public static Direction horizontalSide(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        if (Math.abs(dx) < 1.0E-6 && Math.abs(dz) < 1.0E-6) return null;
        return Direction.getNearest(dx, 0.0, dz);
    }
}
