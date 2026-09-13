package com.astune.gyromancy.api.field;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * An immutable rectangular field shape centred on its owning field.
 * Dimensions are expressed in world blocks. Its arbitrary orientation is
 * supplied by the field's bound {@link ShapeOrientation} at query time.
 */
public record RectangularFieldShape(float length, float width, float height)
        implements MagicFieldShape {
    public static final float DEFAULT_SIZE = 1.0F;
    public static final float MAX_SIZE = 16.0F;

    public RectangularFieldShape {
        validateDimension(length, "length");
        validateDimension(width, "width");
        validateDimension(height, "height");
    }

    /** Creates the default one-block cube. */
    public RectangularFieldShape() {
        this(DEFAULT_SIZE, DEFAULT_SIZE, DEFAULT_SIZE);
    }

    @Override
    public AABB bounds() {
        return bounds(ShapeOrientation.IDENTITY);
    }

    @Override
    public AABB bounds(ShapeOrientation orientation) {
        if (orientation == null) throw new IllegalArgumentException("orientation cannot be null");
        return orientation.boundsFor(unorientedBounds());
    }

    /**
     * Local +Z is the bound orientation's forward axis, so length extends
     * forward/backward, width extends right/left, and height extends up/down.
     */
    private AABB unorientedBounds() {
        double halfLength = length * 0.5;
        double halfWidth = width * 0.5;
        double halfHeight = height * 0.5;
        return new AABB(-halfWidth, -halfHeight, -halfLength,
                halfWidth, halfHeight, halfLength);
    }

    @Override
    public boolean isInside(Vec3 point) {
        return isInside(point, ShapeOrientation.IDENTITY);
    }

    @Override
    public boolean isInside(Vec3 point, ShapeOrientation orientation) {
        if (point == null) return false;
        if (orientation == null) throw new IllegalArgumentException("orientation cannot be null");
        Vec3 localPoint = orientation.toLocalSpace(point);
        return Math.abs(localPoint.x) <= width * 0.5
                && Math.abs(localPoint.y) <= height * 0.5
                && Math.abs(localPoint.z) <= length * 0.5;
    }

    @Override
    public double supportDistance(ShapeOrientation orientation, Vec3 direction) {
        if (orientation == null) throw new IllegalArgumentException("orientation cannot be null");
        if (direction == null || !Double.isFinite(direction.x) || !Double.isFinite(direction.y)
                || !Double.isFinite(direction.z) || direction.lengthSqr() < 1.0E-12) {
            throw new IllegalArgumentException("support direction must be finite and non-zero");
        }
        Vec3 unit = direction.normalize();
        return Math.abs(unit.dot(orientation.rightAxis())) * width * 0.5
                + Math.abs(unit.dot(orientation.upAxis())) * height * 0.5
                + Math.abs(unit.dot(orientation.forward())) * length * 0.5;
    }

    @Override
    public double volume() {
        return (double) length * width * height;
    }

    private static void validateDimension(float dimension, String name) {
        if (!Float.isFinite(dimension) || dimension <= 0.0F || dimension > MAX_SIZE) {
            throw new IllegalArgumentException(name + " must be finite, positive, and at most " + MAX_SIZE);
        }
    }
}
