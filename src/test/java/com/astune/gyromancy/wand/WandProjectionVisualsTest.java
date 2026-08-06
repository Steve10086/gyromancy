package com.astune.gyromancy.wand;

import com.astune.gyromancy.api.geometry.SurfaceFrame;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WandProjectionVisualsTest {
    @Test
    void rollRotatesBothSurfaceAxesAroundTheUnchangedNormal() {
        SurfaceFrame base = SurfaceFrame.facing(
                new Vec3(1.0, 2.0, 3.0),
                new Vec3(0.0, 0.0, -1.0),
                new Vec3(0.0, 1.0, 0.0));

        SurfaceFrame rolled = WandProjectionVisuals.rolledFrame(base, 90.0);

        assertEquals(base.origin(), rolled.origin());
        assertEquals(base.normal(), rolled.normal());
        assertTrue(base.normal().cross(base.axisU()).distanceToSqr(rolled.axisU()) < 1e-12);
        assertTrue(base.normal().cross(base.axisV()).distanceToSqr(rolled.axisV()) < 1e-12);
    }

    @Test
    void entranceScaleReachesFullSizeAfterTwentyTicks() {
        assertEquals(0.0F, WandProjectionVisuals.entranceScale(0.0), 1e-6F);
        assertEquals(0.5F, WandProjectionVisuals.entranceScale(10.0), 1e-6F);
        assertEquals(1.0F, WandProjectionVisuals.entranceScale(20.0), 1e-6F);
        assertEquals(1.0F, WandProjectionVisuals.entranceScale(40.0), 1e-6F);
    }
}
