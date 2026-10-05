package com.astune.gyromancy.client.entity;

import com.astune.gyromancy.entity.ball.MagicBallEntity;
import net.minecraft.util.Mth;

import java.util.Map;

final class FireballRenderColors {
    static final int[] COLORS = {
            0xFFfc4339, 0xFFff7700, 0xFFffd900,
            0xFFD8D0C0, 0xFF80A5F3, 0xFFd000ff
    };
    private static final float DEBUG_COLOR_TICKS = 10.0F;
    private static final float ELEMENT_COLOR_TICKS = 10.0F;

    private FireballRenderColors() {}

    static int debugColor(MagicBallEntity entity, float partialTick) {
        float cycle = (entity.tickCount + partialTick) / DEBUG_COLOR_TICKS;
        int from = Mth.floor(cycle) % COLORS.length;
        int to = (from + 1) % COLORS.length;
        return lerpColor(COLORS[from], COLORS[to], cycle - Mth.floor(cycle));
    }

    static <T extends MagicBallEntity> int elementColor(T entity, float partialTick,
                                                        Map<T, ColorTransition> transitions) {
        return elementColor(entity, entity.getAverageElementLevel(), partialTick, transitions);
    }

    static <T extends MagicBallEntity> int elementColor(T entity, double averageElementLevel, float partialTick,
                                                        Map<T, ColorTransition> transitions) {
        int target = elementTargetColor(averageElementLevel);
        ColorTransition transition = transitions.get(entity);
        if (transition == null) {
            transitions.put(entity, new ColorTransition(target, target, entity.tickCount));
            return target;
        }
        if (transition.target != target) {
            transition = new ColorTransition(transition.color(entity.tickCount + partialTick), target, entity.tickCount);
            transitions.put(entity, transition);
        }
        return transition.color(entity.tickCount + partialTick);
    }

    static int elementColorForAverage(double averageElementLevel) {
        return elementTargetColor(averageElementLevel);
    }

    private static int elementTargetColor(double averageElementLevel) {
        float tier = (float) Math.sqrt(Math.max(0.0, averageElementLevel) / 1000.0);
        if (tier <= 1.0F) return COLORS[0];
        if (tier >= COLORS.length) return COLORS[COLORS.length - 1];

        int from = Mth.floor(tier) - 1;
        return lerpColor(COLORS[from], COLORS[from + 1], tier - Mth.floor(tier));
    }

    private static int lerpColor(int from, int to, float t) {
        int a = lerpChannel((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, t);
        int r = lerpChannel((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = lerpChannel((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = lerpChannel(from & 0xFF, to & 0xFF, t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int lerpChannel(int from, int to, float t) {
        return Mth.clamp(Math.round(Mth.lerp(t, from, to)), 0, 255);
    }

    record ColorTransition(int from, int target, int startTick) {
        int color(float tick) {
            return lerpColor(from, target, Mth.clamp((tick - startTick) / ELEMENT_COLOR_TICKS, 0.0F, 1.0F));
        }
    }
}
