package com.astune.gyromancy.entity;

import com.astune.gyromancy.api.geometry.SurfaceFrame;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArrayRelativePositionTest {
    @Test
    void preservesLocalPositionWhenArrayMovesAndRotates() {
        SurfaceFrame initial = SurfaceFrame.facing(
                new Vec3(1.0, 2.0, 3.0), new Vec3(0.0, 0.0, -1.0),
                new Vec3(0.0, 1.0, 0.0));
        Vec3 initialCenter = new Vec3(1.0, 2.0, 3.0);
        Vec3 position = initialCenter
                .add(initial.axisU().scale(0.75))
                .add(initial.axisV().scale(-0.25))
                .add(initial.normal().scale(2.0));

        ArrayRelativePosition relative = ArrayRelativePosition.capture(
                position, initialCenter, initial);
        SurfaceFrame moved = SurfaceFrame.facing(
                new Vec3(8.0, 5.0, -2.0), new Vec3(1.0, 0.0, 0.0),
                new Vec3(0.0, 1.0, 0.0));
        Vec3 resolved = relative.resolve(moved.origin(), moved);

        assertEquals(moved.origin()
                .add(moved.axisU().scale(0.75))
                .add(moved.axisV().scale(-0.25))
                .add(moved.normal().scale(2.0)), resolved);
    }
}
