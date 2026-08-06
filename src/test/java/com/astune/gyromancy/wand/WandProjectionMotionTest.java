package com.astune.gyromancy.wand;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WandProjectionMotionTest {
    @Test
    void positionMovesPartwayTowardNearbyTarget() {
        Vec3 next = WandProjectionMotion.smoothPosition(
                Vec3.ZERO, new Vec3(2.0, 0.0, 0.0));

        assertEquals(2.0 * WandProjectionMotion.POSITION_RESPONSE, next.x, 1.0E-9);
        assertEquals(0.0, next.y, 1.0E-9);
        assertEquals(0.0, next.z, 1.0E-9);
    }

    @Test
    void distantTargetSnapsInsteadOfCrossingTheWorldSlowly() {
        Vec3 target = new Vec3(20.0, 5.0, -4.0);

        assertEquals(target, WandProjectionMotion.smoothPosition(Vec3.ZERO, target));
    }

    @Test
    void directionUsesAUnitLengthArcTowardTarget() {
        Vec3 current = new Vec3(0.0, 0.0, -1.0);
        Vec3 target = new Vec3(1.0, 0.0, 0.0);
        Vec3 next = WandProjectionMotion.smoothDirection(current, target);

        assertEquals(1.0, next.length(), 1.0E-9);
        assertTrue(next.dot(target) > current.dot(target));
        assertTrue(next.dot(current) > 0.0);
    }

    @Test
    void oppositeDirectionDoesNotCollapseToZeroOrRemainStuck() {
        Vec3 current = new Vec3(0.0, 0.0, -1.0);
        Vec3 target = current.scale(-1.0);
        Vec3 next = WandProjectionMotion.smoothDirection(current, target);

        assertEquals(1.0, next.length(), 1.0E-9);
        assertTrue(next.distanceToSqr(current) > 0.0);
        assertTrue(next.dot(target) > current.dot(target));
    }

    @Test
    void renderInterpolationPreservesDirectionEndpoints() {
        Vec3 previous = new Vec3(0.0, 0.0, -1.0);
        Vec3 current = new Vec3(1.0, 0.0, 0.0);

        assertEquals(0.0, previous.distanceTo(
                WandProjectionMotion.interpolateDirection(previous, current, 0.0)), 1.0E-9);
        assertEquals(0.0, current.distanceTo(
                WandProjectionMotion.interpolateDirection(previous, current, 1.0)), 1.0E-9);
        assertEquals(1.0,
                WandProjectionMotion.interpolateDirection(previous, current, 0.5).length(),
                1.0E-9);
    }

    @Test
    void rollTargetAdvancesIndependentlyFromTheSmoothedPose() {
        float target = WandProjectionMotion.advanceRollTarget(179.0F, 2.0F);
        float next = WandProjectionMotion.smoothRoll(170.0F, target);

        assertEquals(-179.0F, target, 1.0E-6F);
        assertTrue(Mth.wrapDegrees(next - 170.0F) > 0.0F);
        assertTrue(Mth.wrapDegrees(target - next) > 0.0F);
    }

    @Test
    void renderRollInterpolationUsesTheShortestWrappedArc() {
        float halfway = WandProjectionMotion.interpolateRoll(179.0F, -179.0F, 0.5F);

        assertEquals(180.0F, Math.abs(halfway), 1.0E-6F);
    }
}
