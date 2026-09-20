package com.astune.gyromancy.compile.vector;

import com.astune.gyromancy.api.geometry.SurfaceFrame;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Runtime inputs shared by all dynamic VectorOps. */
public record VectorContext(
        Optional<SurfaceFrame> compileFrame,
        Optional<SurfaceFrame> activationFrame,
        Optional<SurfaceFrame> liveFrame,
        Vec3 velocity,
        Vec3 facing,
        Vec3 arrayNormal,
        long tick,
        double gravity
) {
    public VectorContext {
        compileFrame = compileFrame == null ? Optional.empty() : compileFrame;
        activationFrame = activationFrame == null ? Optional.empty() : activationFrame;
        liveFrame = liveFrame == null ? Optional.empty() : liveFrame;
        velocity = velocity == null ? Vec3.ZERO : velocity;
        facing = facing == null ? Vec3.ZERO : facing;
        arrayNormal = arrayNormal == null ? Vec3.ZERO : arrayNormal;
        gravity = Double.isFinite(gravity) ? gravity : 0.0;
    }

    public VectorContext(SurfaceFrame compileFrame, SurfaceFrame activationFrame,
                         SurfaceFrame liveFrame, Vec3 velocity, Vec3 facing,
                         Vec3 arrayNormal) {
        this(Optional.ofNullable(compileFrame), Optional.ofNullable(activationFrame),
                Optional.ofNullable(liveFrame), velocity, facing, arrayNormal, 0L, 0.0);
    }

    public VectorContext(SurfaceFrame compileFrame, SurfaceFrame activationFrame,
                         SurfaceFrame liveFrame, Vec3 velocity, Vec3 facing,
                         Vec3 arrayNormal, long tick) {
        this(Optional.ofNullable(compileFrame), Optional.ofNullable(activationFrame),
                Optional.ofNullable(liveFrame), velocity, facing, arrayNormal, tick, 0.0);
    }

    public VectorContext(Optional<SurfaceFrame> compileFrame,
                         Optional<SurfaceFrame> activationFrame,
                         Optional<SurfaceFrame> liveFrame, Vec3 velocity,
                         Vec3 facing, Vec3 arrayNormal) {
        this(compileFrame, activationFrame, liveFrame, velocity, facing, arrayNormal, 0L, 0.0);
    }

    public VectorContext(Optional<SurfaceFrame> compileFrame,
                         Optional<SurfaceFrame> activationFrame,
                         Optional<SurfaceFrame> liveFrame, Vec3 velocity,
                         Vec3 facing, Vec3 arrayNormal, long tick) {
        this(compileFrame, activationFrame, liveFrame, velocity, facing, arrayNormal, tick, 0.0);
    }

}
