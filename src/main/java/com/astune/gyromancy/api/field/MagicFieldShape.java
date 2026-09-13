package com.astune.gyromancy.api.field;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Immutable geometry supplied by the caller for a {@code MagicFieldEntity}.
 *
 * <p>Implementations must return the same volume and field-relative bounds
 * for their lifetime. A field supplies its direction-bound
 * {@link ShapeOrientation} when querying oriented shapes, then only
 * translates the result to its world position. A field keeps the supplied
 * shape reference and deliberately does not expose a way to replace it after
 * construction.</p>
 */
public interface MagicFieldShape {
    /**
     * The axis-aligned field-relative bounds enclosing this shape. For an
     * oriented shape, this is its rotated broad-phase bounding box rather
     * than its unrotated local box. Exact membership is determined by
     * {@link #isInside(Vec3)}.
     */
    AABB bounds();

    /**
     * The field-relative broad-phase bounds for a supplied field orientation.
     * Shapes that have no directional geometry may retain {@link #bounds()}.
     */
    default AABB bounds(ShapeOrientation orientation) {
        if (orientation == null) throw new IllegalArgumentException("orientation cannot be null");
        return bounds();
    }

    /** Returns whether a field-relative {@code point} is inside this shape. */
    boolean isInside(Vec3 point);

    /**
     * Tests a field-relative point with the orientation bound to its field.
     * Direction-independent shapes may retain {@link #isInside(Vec3)}.
     */
    default boolean isInside(Vec3 point, ShapeOrientation orientation) {
        if (orientation == null) throw new IllegalArgumentException("orientation cannot be null");
        return isInside(point);
    }

    /**
     * Returns the distance from the field origin to this shape's supporting
     * boundary in {@code direction}. Field creation uses this to place the
     * rear boundary flush with its source surface. Shapes with geometry more
     * precise than their broad-phase bounds should override this method.
     */
    default double supportDistance(ShapeOrientation orientation, Vec3 direction) {
        if (orientation == null) throw new IllegalArgumentException("orientation cannot be null");
        if (direction == null || !Double.isFinite(direction.x) || !Double.isFinite(direction.y)
                || !Double.isFinite(direction.z) || direction.lengthSqr() < 1.0E-12) {
            throw new IllegalArgumentException("support direction must be finite and non-zero");
        }
        Vec3 unit = direction.normalize();
        AABB bounds = bounds(orientation);
        double maximum = Double.NEGATIVE_INFINITY;
        for (double x : new double[]{bounds.minX, bounds.maxX}) {
            for (double y : new double[]{bounds.minY, bounds.maxY}) {
                for (double z : new double[]{bounds.minZ, bounds.maxZ}) {
                    maximum = Math.max(maximum, new Vec3(x, y, z).dot(unit));
                }
            }
        }
        if (!Double.isFinite(maximum) || maximum < 0.0) {
            throw new IllegalArgumentException("shape support distance must be finite and non-negative");
        }
        return maximum;
    }

    /** Returns the fixed volume occupied by this shape. */
    double volume();

    /**
     * Calculates the field's average energy. Volume-based shapes can use the
     * default energy-density calculation; specialised shapes may override it.
     */
    default double averageEnergy(double energy) {
        double volume = volume();
        if (!Double.isFinite(volume) || volume <= 0.0) {
            throw new IllegalStateException("Magic field shape volume must be finite and positive");
        }
        return energy / volume;
    }
}
