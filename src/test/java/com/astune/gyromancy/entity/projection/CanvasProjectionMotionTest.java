package com.astune.gyromancy.entity.projection;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasProjectionMotionTest {
    @Test
    void positionMovesPartwayTowardNearbyTarget() {
        Vec3 next = CanvasProjectionMotion.smoothPosition(
                Vec3.ZERO, new Vec3(2.0, 0.0, 0.0));

        assertEquals(2.0 * CanvasProjectionMotion.POSITION_RESPONSE, next.x, 1.0E-9);
        assertEquals(0.0, next.y, 1.0E-9);
        assertEquals(0.0, next.z, 1.0E-9);
    }

    @Test
    void distantTargetSnapsInsteadOfCrossingTheWorldSlowly() {
        Vec3 target = new Vec3(20.0, 5.0, -4.0);

        assertEquals(target, CanvasProjectionMotion.smoothPosition(Vec3.ZERO, target));
    }

    @Test
    void directionUsesAUnitLengthArcTowardTarget() {
        Vec3 current = new Vec3(0.0, 0.0, -1.0);
        Vec3 target = new Vec3(1.0, 0.0, 0.0);
        Vec3 next = CanvasProjectionMotion.smoothDirection(current, target);

        assertEquals(1.0, next.length(), 1.0E-9);
        assertTrue(next.dot(target) > current.dot(target));
        assertTrue(next.dot(current) > 0.0);
    }

    @Test
    void oppositeDirectionDoesNotCollapseToZeroOrRemainStuck() {
        Vec3 current = new Vec3(0.0, 0.0, -1.0);
        Vec3 target = current.scale(-1.0);
        Vec3 next = CanvasProjectionMotion.smoothDirection(current, target);

        assertEquals(1.0, next.length(), 1.0E-9);
        assertTrue(next.distanceToSqr(current) > 0.0);
        assertTrue(next.dot(target) > current.dot(target));
    }

    @Test
    void renderInterpolationPreservesDirectionEndpoints() {
        Vec3 previous = new Vec3(0.0, 0.0, -1.0);
        Vec3 current = new Vec3(1.0, 0.0, 0.0);

        assertEquals(0.0, previous.distanceTo(
                CanvasProjectionMotion.interpolateDirection(previous, current, 0.0)), 1.0E-9);
        assertEquals(0.0, current.distanceTo(
                CanvasProjectionMotion.interpolateDirection(previous, current, 1.0)), 1.0E-9);
        assertEquals(1.0,
                CanvasProjectionMotion.interpolateDirection(previous, current, 0.5).length(),
                1.0E-9);
    }

    @Test
    void rollTargetWrapsAndSmoothRollUsesCurrentResponse() {
        float target = CanvasProjectionMotion.advanceRollTarget(179.0F, 2.0F);
        float next = CanvasProjectionMotion.smoothRoll(170.0F, target);

        assertEquals(-179.0F, target, 1.0E-6F);
        assertEquals(target, next, 1.0E-6F);
    }

    @Test
    void renderRollInterpolationUsesTheShortestWrappedArc() {
        float halfway = CanvasProjectionMotion.interpolateRoll(179.0F, -179.0F, 0.5F);

        assertEquals(180.0F, Math.abs(halfway), 1.0E-6F);
    }
}
