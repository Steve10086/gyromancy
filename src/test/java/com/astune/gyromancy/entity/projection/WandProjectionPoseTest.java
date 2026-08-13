package com.astune.gyromancy.entity.projection;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WandProjectionPoseTest {
    @Test
    void targetIsPlacedToTheViewRightWithoutChangingForwardDistance() {
        Vec3 target = WandProjectionPose.targetCenter(
                Vec3.ZERO, new Vec3(0.0, 0.0, 1.0), 2.0, 0.0F, false);

        assertEquals(-WandProjectionPose.RIGHT_OFFSET, target.x, 1.0E-9);
        assertEquals(0.0, target.y, 1.0E-9);
        assertEquals(2.0, target.z, 1.0E-9);
    }

    @Test
    void rightOffsetRemainsHorizontalWhenLookingUp() {
        Vec3 right = WandProjectionPose.rightDirection(
                new Vec3(0.0, 1.0, 0.0), 0.0F);

        assertEquals(0.0, right.y, 1.0E-9);
        assertEquals(1.0, right.length(), 1.0E-9);
    }

    @Test
    void mirroredOffsetSwitchesSides() {
        Vec3 normal = WandProjectionPose.targetCenter(
                Vec3.ZERO, new Vec3(0.0, 0.0, 1.0), 2.0, 0.0F, false);
        Vec3 mirrored = WandProjectionPose.targetCenter(
                Vec3.ZERO, new Vec3(0.0, 0.0, 1.0), 2.0, 0.0F, true);

        assertEquals(-mirrored.x, normal.x, 1.0E-9);
        assertEquals(normal.z, mirrored.z, 1.0E-9);
    }

    @Test
    void actualLeftHandIsMirroredIncludingLeftHandedPlayers() {
        assertTrue(WandProjectionPose.mirrorForHand(
                HumanoidArm.RIGHT, InteractionHand.OFF_HAND));
        assertTrue(WandProjectionPose.mirrorForHand(
                HumanoidArm.LEFT, InteractionHand.MAIN_HAND));
    }
}
