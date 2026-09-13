package com.astune.gyromancy.api.field;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Immutable three-dimensional orientation bound to a field direction.
 *
 * <p>{@link #forward()} defines the local positive-Z axis and
 * {@link #rollDegrees()} rotates the local X/Y axes around it. A field owns
 * one orientation that is copied from its {@link FieldDirection}; keeping
 * this as a separate value lets shapes consume a compact two-axis basis
 * without owning an unrelated forward direction.</p>
 */
public record ShapeOrientation(Vec3 forward, float rollDegrees) {
    public static final ShapeOrientation IDENTITY = new ShapeOrientation(new Vec3(0.0, 0.0, 1.0), 0.0F);

    public ShapeOrientation {
        if (!isFinite(forward) || forward.lengthSqr() < 1.0E-12) {
            throw new IllegalArgumentException("Shape orientation forward vector must be finite and non-zero");
        }
        if (!Float.isFinite(rollDegrees)) {
            throw new IllegalArgumentException("Shape orientation roll must be finite");
        }
        forward = forward.normalize();
        rollDegrees = normalizeDegrees(rollDegrees);
    }

    /** Copies the forward vector and roll from the field direction it is bound to. */
    public ShapeOrientation(FieldDirection direction) {
        this(copyForward(direction), direction.angleDegrees());
    }

    /** Local positive-X axis in field-relative coordinates. */
    public Vec3 rightAxis() {
        return basis().right();
    }

    /** Local positive-Y axis in field-relative coordinates. */
    public Vec3 upAxis() {
        return basis().up();
    }

    /** Rotates a local shape offset into its field-relative world offset. */
    public Vec3 toFieldSpace(Vec3 localPoint) {
        if (localPoint == null) throw new IllegalArgumentException("localPoint cannot be null");
        Basis basis = basis();
        return basis.right().scale(localPoint.x)
                .add(basis.up().scale(localPoint.y))
                .add(forward.scale(localPoint.z));
    }

    /** Converts a field-relative world offset to this shape's local axes. */
    public Vec3 toLocalSpace(Vec3 fieldPoint) {
        if (fieldPoint == null) throw new IllegalArgumentException("fieldPoint cannot be null");
        Basis basis = basis();
        return new Vec3(fieldPoint.dot(basis.right()), fieldPoint.dot(basis.up()), fieldPoint.dot(forward));
    }

    /** Returns the axis-aligned field-relative bounds enclosing a local box. */
    public AABB boundsFor(AABB localBounds) {
        if (localBounds == null) throw new IllegalArgumentException("localBounds cannot be null");

        AABB bounds = null;
        for (double x : new double[]{localBounds.minX, localBounds.maxX}) {
            for (double y : new double[]{localBounds.minY, localBounds.maxY}) {
                for (double z : new double[]{localBounds.minZ, localBounds.maxZ}) {
                    Vec3 corner = toFieldSpace(new Vec3(x, y, z));
                    bounds = bounds == null
                            ? new AABB(corner, corner)
                            : bounds.minmax(new AABB(corner, corner));
                }
            }
        }
        return bounds;
    }

    private Basis basis() {
        Vec3 referenceUp = Math.abs(forward.y) < 0.999
                ? new Vec3(0.0, 1.0, 0.0)
                : new Vec3(0.0, 0.0, 1.0);
        Vec3 unrolledRight = referenceUp.cross(forward).normalize();
        Vec3 unrolledUp = forward.cross(unrolledRight).normalize();
        double radians = Math.toRadians(rollDegrees);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return new Basis(
                unrolledRight.scale(cosine).add(unrolledUp.scale(sine)),
                unrolledUp.scale(cosine).subtract(unrolledRight.scale(sine)));
    }

    private static boolean isFinite(Vec3 vector) {
        return vector != null && Double.isFinite(vector.x)
                && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private static Vec3 copyForward(FieldDirection direction) {
        if (direction == null) throw new IllegalArgumentException("direction cannot be null");
        Vec3 forward = direction.vector();
        return new Vec3(forward.x, forward.y, forward.z);
    }

    private static float normalizeDegrees(float degrees) {
        float normalized = degrees % 360.0F;
        return normalized < 0.0F ? normalized + 360.0F : normalized;
    }

    private record Basis(Vec3 right, Vec3 up) {}
}
