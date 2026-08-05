package com.astune.gyromancy.wand;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Tick-level smoothing shared by a projection's position and free direction. */
final class WandProjectionMotion {
    static final double POSITION_RESPONSE = 0.8;
    static final double DIRECTION_RESPONSE = 0.8;
    static final double TELEPORT_SNAP_DISTANCE = 8.0;
    private static final double PARALLEL_DOT = 0.9995;

    private WandProjectionMotion() {}

    static Vec3 smoothPosition(Vec3 current, Vec3 target) {
        if (current.distanceToSqr(target)
                >= TELEPORT_SNAP_DISTANCE * TELEPORT_SNAP_DISTANCE) {
            return target;
        }
        return current.lerp(target, POSITION_RESPONSE);
    }

    static Vec3 smoothDirection(Vec3 current, Vec3 target) {
        return interpolateDirection(current, target, DIRECTION_RESPONSE);
    }

    static Vec3 interpolateDirection(Vec3 current, Vec3 target, double progress) {
        Vec3 from = current.normalize();
        Vec3 to = target.normalize();
        double interpolation = Mth.clamp(progress, 0.0, 1.0);
        double dot = Mth.clamp(from.dot(to), -1.0, 1.0);
        if (dot >= PARALLEL_DOT) {
            return from.lerp(to, interpolation).normalize();
        }
        if (dot <= -PARALLEL_DOT) {
            Vec3 reference = Math.abs(from.y) < 0.9
                    ? new Vec3(0.0, 1.0, 0.0)
                    : new Vec3(1.0, 0.0, 0.0);
            Vec3 tangent = reference.subtract(from.scale(reference.dot(from))).normalize();
            double angle = Math.PI * interpolation;
            return from.scale(Math.cos(angle)).add(tangent.scale(Math.sin(angle))).normalize();
        }

        double angle = Math.acos(dot) * interpolation;
        Vec3 tangent = to.subtract(from.scale(dot)).normalize();
        return from.scale(Math.cos(angle)).add(tangent.scale(Math.sin(angle))).normalize();
    }
}
