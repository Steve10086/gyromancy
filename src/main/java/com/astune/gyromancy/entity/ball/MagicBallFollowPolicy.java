package com.astune.gyromancy.entity.ball;

import com.astune.gyromancy.api.array.ArrayObject;
import net.minecraft.world.phys.Vec3;

/** Pure eligibility policy invoked by MagicBallEntity's own tick. */
final class MagicBallFollowPolicy {
    static final int FOLLOW_TICKS = 20;

    private MagicBallFollowPolicy() {}

    static boolean shouldFollow(long gameTime, long compilationEffectEndTick,
                                Vec3 velocity, Vec3 acceleration) {
        long activatedTick = compilationEffectEndTick - ArrayObject.COMPILATION_EFFECT_TICKS;
        long age = gameTime - activatedTick;
        return compilationEffectEndTick > 0L
                && age >= 0L && age < FOLLOW_TICKS
                && velocity.lengthSqr() == 0.0
                && acceleration.lengthSqr() == 0.0;
    }
}
