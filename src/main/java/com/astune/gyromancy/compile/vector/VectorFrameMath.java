package com.astune.gyromancy.compile.vector;

import net.minecraft.world.phys.Vec3;

/** Coordinate and rotation helpers shared by frame-aware VectorOps. */
final class VectorFrameMath {
    private static final double EPSILON = 1.0E-8;
    private static final Vec3 WORLD_X = new Vec3(1.0, 0.0, 0.0);
    private static final Vec3 WORLD_Y = new Vec3(0.0, 1.0, 0.0);
    private static final Vec3 WORLD_Z = new Vec3(0.0, 0.0, 1.0);

    private VectorFrameMath() {}

    static Vec3 movementAxis(VectorContext context) {
        Vec3 axis = VectorComposition.directionOrZero(context.velocity());
        if (axis.lengthSqr() < EPSILON) axis = VectorComposition.directionOrZero(context.facing());
        if (axis.lengthSqr() < EPSILON) axis = VectorComposition.directionOrZero(context.arrayNormal());
        return axis;
    }

    /** Interprets a vector in a frame whose local y-axis is entity movement. */
    static Vec3 orientByMovement(Vec3 local, VectorContext context) {
        if (local == null || local.lengthSqr() < EPSILON) return Vec3.ZERO;
        Vec3 y = movementAxis(context);
        if (y.lengthSqr() < EPSILON) return local;

        Vec3 reference = Math.abs(y.dot(WORLD_Y)) < 1.0 - EPSILON ? WORLD_Y : WORLD_X;
        Vec3 x = reference.subtract(y.scale(reference.dot(y)));
        if (x.lengthSqr() < EPSILON) {
            reference = WORLD_Z;
            x = reference.subtract(y.scale(reference.dot(y)));
        }
        x = x.normalize();
        Vec3 z = x.cross(y).normalize();
        return x.scale(local.x).add(y.scale(local.y)).add(z.scale(local.z));
    }

    static Vec3 rotateAroundAxis(Vec3 vector, Vec3 axis, double radians) {
        Vec3 normalizedAxis = VectorComposition.directionOrZero(axis);
        if (vector == null || vector.lengthSqr() < EPSILON
                || normalizedAxis.lengthSqr() < EPSILON || Math.abs(radians) < EPSILON) {
            return vector == null ? Vec3.ZERO : vector;
        }
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return vector.scale(cosine)
                .add(normalizedAxis.cross(vector).scale(sine))
                .add(normalizedAxis.scale(normalizedAxis.dot(vector) * (1.0 - cosine)));
    }

    static Vec3 worldYRotationTo(Vec3 target) {
        Vec3 direction = VectorComposition.directionOrZero(target);
        if (direction.lengthSqr() < EPSILON) return Vec3.ZERO;

        double dot = Math.max(-1.0, Math.min(1.0, WORLD_Y.dot(direction)));
        Vec3 axis = WORLD_Y.cross(direction);
        if (axis.lengthSqr() < EPSILON) {
            return dot > 0.0 ? Vec3.ZERO : WORLD_X.scale(Math.PI);
        }
        return axis.normalize().scale(Math.acos(dot));
    }
}
