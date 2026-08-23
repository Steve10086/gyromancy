package com.astune.gyromancy.entity.ball;

import com.astune.gyromancy.api.array.ArrayObject;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicBallFollowPolicyTest {
    private static final long ACTIVATED_TICK = 1_000L;
    private static final long EFFECT_END_TICK =
            ACTIVATED_TICK + ArrayObject.COMPILATION_EFFECT_TICKS;

    @Test
    void followsOnlyDuringTheArraysFirstTwentyTicks() {
        assertTrue(MagicBallFollowPolicy.shouldFollow(
                ACTIVATED_TICK, EFFECT_END_TICK, Vec3.ZERO, Vec3.ZERO));
        assertTrue(MagicBallFollowPolicy.shouldFollow(
                ACTIVATED_TICK + MagicBallFollowPolicy.FOLLOW_TICKS - 1, EFFECT_END_TICK, Vec3.ZERO, Vec3.ZERO));
        assertFalse(MagicBallFollowPolicy.shouldFollow(
                ACTIVATED_TICK + MagicBallFollowPolicy.FOLLOW_TICKS, EFFECT_END_TICK, Vec3.ZERO, Vec3.ZERO));
    }

    @Test
    void rejectsTimeBeforeActivationAndLegacyArraysWithoutATimestamp() {
        assertFalse(MagicBallFollowPolicy.shouldFollow(
                ACTIVATED_TICK - 1L, EFFECT_END_TICK, Vec3.ZERO, Vec3.ZERO));
        assertFalse(MagicBallFollowPolicy.shouldFollow(
                ACTIVATED_TICK, 0L, Vec3.ZERO, Vec3.ZERO));
    }

    @Test
    void activeVelocityOrAccelerationStopsFollowing() {
        assertFalse(MagicBallFollowPolicy.shouldFollow(
                ACTIVATED_TICK, EFFECT_END_TICK, new Vec3(0.01, 0.0, 0.0), Vec3.ZERO));
        assertFalse(MagicBallFollowPolicy.shouldFollow(
                ACTIVATED_TICK, EFFECT_END_TICK, Vec3.ZERO, new Vec3(0.0, -0.01, 0.0)));
    }
}
