package com.astune.gyromancy.client.effect;

import com.astune.gyromancy.api.element.ElementType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side presentation of a crystal spawn: the grow-in animation state and
 * the one-shot spawn effect.
 *
 * <p>The spawn effect plays when the position is chosen; the block itself
 * appears a few ticks later and starts the grow-in then.
 */
@OnlyIn(Dist.CLIENT)
public final class CrystalSpawnEffects {
    /** Ticks the crystal takes to grow from {@link #START_SCALE} to full size. */
    public static final int GROWTH_TICKS = 20;
    /** Model scale at the start of the grow-in. */
    public static final float START_SCALE = 0.1F;

    private static final Map<BlockPos, Long> GROWTH_STARTS = new HashMap<>();

    private CrystalSpawnEffects() {}

    /** Handles a spawn-FX notice: plays the tinted spawn effect in place. */
    public static void onSpawnFx(BlockPos pos, int elementIndex) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        CrystalSpawnEffect.play(level, pos.immutable(), ElementType.byIndex(elementIndex));
    }

    /** Handles a grow notice: starts the crystal's grow-in animation. */
    public static void onGrow(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        startGrowth(pos, level.getGameTime());
    }

    /** Starts the grow-in timer for one crystal. */
    static void startGrowth(BlockPos pos, long gameTime) {
        GROWTH_STARTS.put(pos.immutable(), gameTime);
    }

    /** Current model scale of a crystal, or 1 when it is not growing in. */
    public static float scale(BlockPos pos, long gameTime, float partialTick) {
        Long start = GROWTH_STARTS.get(pos);
        if (start == null) return 1.0F;
        float progress = growthProgress(gameTime - start + partialTick);
        if (progress >= 1.0F) {
            GROWTH_STARTS.remove(pos);
            return 1.0F;
        }
        return START_SCALE + (1.0F - START_SCALE) * progress;
    }

    /** Smoothstepped 0..1 growth progress over {@link #GROWTH_TICKS}. */
    static float growthProgress(float ageTicks) {
        float progress = Mth.clamp(ageTicks / GROWTH_TICKS, 0.0F, 1.0F);
        return progress * progress * (3.0F - 2.0F * progress);
    }

    /** Drops every tracked grow-in when the client level goes away. */
    public static void clear() {
        GROWTH_STARTS.clear();
    }
}
