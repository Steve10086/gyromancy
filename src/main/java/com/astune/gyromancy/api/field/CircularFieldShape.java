package com.astune.gyromancy.api.field;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * An immutable, three-dimensional circular field shape represented by a
 * stretched sphere (an ellipsoid) centred on its owning field.
 *
 * <p>Length follows local +Z (the orientation's forward axis), width follows
 * local +X (the right axis), and height follows local +Y (the up axis).</p>
 */
public record CircularFieldShape(float length, float width, float height)
        implements MagicFieldShape {
    public static final float DEFAULT_SIZE = 1.0F;
    public static final float MAX_SIZE = 16.0F;

    public CircularFieldShape {
        validateDimension(length, "length");
        validateDimension(width, "width");
        validateDimension(height, "height");
    }

    /** Creates the default one-block sphere. */
    public CircularFieldShape() {
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

    private AABB unorientedBounds() {
        return new AABB(
                -width * 0.5, -height * 0.5, -length * 0.5,
                width * 0.5, height * 0.5, length * 0.5);
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
        double widthRadius = width * 0.5;
        double heightRadius = height * 0.5;
        double lengthRadius = length * 0.5;
        double normalizedDistance = square(localPoint.x / widthRadius)
                + square(localPoint.y / heightRadius)
                + square(localPoint.z / lengthRadius);
        return normalizedDistance <= 1.0;
    }

    @Override
    public double supportDistance(ShapeOrientation orientation, Vec3 direction) {
        if (orientation == null) throw new IllegalArgumentException("orientation cannot be null");
        if (direction == null || !Double.isFinite(direction.x) || !Double.isFinite(direction.y)
                || !Double.isFinite(direction.z) || direction.lengthSqr() < 1.0E-12) {
            throw new IllegalArgumentException("support direction must be finite and non-zero");
        }

        Vec3 unit = direction.normalize();
        double rightComponent = unit.dot(orientation.rightAxis()) * width * 0.5;
        double upComponent = unit.dot(orientation.upAxis()) * height * 0.5;
        double forwardComponent = unit.dot(orientation.forward()) * length * 0.5;
        return Math.sqrt(square(rightComponent) + square(upComponent) + square(forwardComponent));
    }

    @Override
    public double volume() {
        return Math.PI * length * width * height / 6.0;
    }

    private static double square(double value) {
        return value * value;
    }

    private static void validateDimension(float dimension, String name) {
        if (!Float.isFinite(dimension) || dimension <= 0.0F || dimension > MAX_SIZE) {
            throw new IllegalArgumentException(name + " must be finite, positive, and at most " + MAX_SIZE);
        }
    }
}
