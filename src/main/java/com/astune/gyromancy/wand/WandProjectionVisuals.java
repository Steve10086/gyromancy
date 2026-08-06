package com.astune.gyromancy.wand;

import com.astune.gyromancy.api.geometry.SurfaceFrame;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Shared projection roll and render-only entrance animation calculations. */
final class WandProjectionVisuals {
    static final int ENTRANCE_TICKS = 20;

    private WandProjectionVisuals() {}

    static SurfaceFrame rolledFrame(SurfaceFrame base, double degrees) {
        double radians = Math.toRadians(degrees);
        Vec3 axisU = rotateAround(base.axisU(), base.normal(), radians);
        Vec3 axisV = rotateAround(base.axisV(), base.normal(), radians);
        return new SurfaceFrame(base.origin(), axisU, axisV, base.normal());
    }

    static float entranceScale(double ageTicks) {
        double progress = Mth.clamp(ageTicks / ENTRANCE_TICKS, 0.0, 1.0);
        return (float) (progress * progress * (3.0 - 2.0 * progress));
    }

    private static Vec3 rotateAround(Vec3 vector, Vec3 axis, double radians) {
        Vec3 normalizedAxis = axis.normalize();
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return vector.scale(cosine)
                .add(normalizedAxis.cross(vector).scale(sine))
                .add(normalizedAxis.scale(normalizedAxis.dot(vector) * (1.0 - cosine)))
                .normalize();
    }
}
