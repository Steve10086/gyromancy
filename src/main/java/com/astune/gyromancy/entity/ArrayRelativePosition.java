package com.astune.gyromancy.entity;

import com.astune.gyromancy.api.geometry.SurfaceFrame;
import net.minecraft.world.phys.Vec3;

/** A three-dimensional position expressed in a magic array's moving frame. */
public record ArrayRelativePosition(double u, double v, double normal) {
    public static ArrayRelativePosition capture(
            Vec3 worldPosition, Vec3 arrayCenter, SurfaceFrame frame) {
        Vec3 relative = worldPosition.subtract(arrayCenter);
        return new ArrayRelativePosition(
                relative.dot(frame.axisU()),
                relative.dot(frame.axisV()),
                relative.dot(frame.normal()));
    }

    public Vec3 resolve(Vec3 arrayCenter, SurfaceFrame frame) {
        return arrayCenter
                .add(frame.axisU().scale(u))
                .add(frame.axisV().scale(v))
                .add(frame.normal().scale(normal));
    }
}
