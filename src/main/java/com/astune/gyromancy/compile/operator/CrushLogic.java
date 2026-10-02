package com.astune.gyromancy.compile.operator;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * Pure rules for the crush instant effect: strength, shell-based mining level,
 * vanilla tier requirements, and the arrow-driven centre offset.
 */
public final class CrushLogic {
    /** Minimum earth concentration every cell in range must hold. */
    public static final long MIN_ELEMENT = 100L;
    /** Element concentration per strength level. */
    public static final long ELEMENT_PER_STRENGTH = 1000L;
    /** Earth consumed from every cell per strength level. */
    public static final long CONSUME_PER_STRENGTH = 100L;

    private CrushLogic() {}

    /** Average earth concentration converts to whole strength levels. */
    public static int strengthForAverage(double average) {
        if (!Double.isFinite(average) || average <= 0.0) return 0;
        return (int) Math.floor(average / ELEMENT_PER_STRENGTH);
    }

    /**
     * Outermost shell is level one; every full block towards the centre adds
     * one, and strength raises the whole sphere.
     */
    public static int shellLevel(double radius, double distance, int strength) {
        int base = (int) Math.floor(radius - Math.max(0.0, distance)) + 1;
        return Math.max(1, base) + Math.max(0, strength);
    }

    /** Vanilla mining tiers: 0 wood, 1 stone, 2 iron, 3 diamond, 4 netherite. */
    public static int requiredTier(BlockState state) {
        if (!state.requiresCorrectToolForDrops()) return 0;
        if (state.is(Tags.Blocks.NEEDS_NETHERITE_TOOL)) return 4;
        if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) return 3;
        if (state.is(BlockTags.NEEDS_IRON_TOOL)) return 2;
        if (state.is(BlockTags.NEEDS_STONE_TOOL)) return 1;
        return 0;
    }

    /** A block is crushed when it is breakable and its tier is below the level. */
    public static boolean canCrush(Level level, BlockPos pos, BlockState state, int tier) {
        if (state.isAir()) return false;
        if (!state.getFluidState().isEmpty()) return false;
        if (state.getDestroySpeed(level, pos) < 0.0F) return false;
        return requiredTier(state) < tier;
    }

    /**
     * The momentum residual of the authored arrows: the summed lengths minus
     * the length of their vector sum. It is non-negative and measures how much
     * the arrows do not reinforce each other.
     */
    public static double upwardComponent(double arrowSizeSum, Vec3 arrowSum) {
        Vec3 safeSum = arrowSum == null ? Vec3.ZERO : arrowSum;
        return Math.max(0.0, arrowSizeSum - safeSum.length());
    }

    /**
     * The centre starts one radius behind the array plane; the arrows' momentum
     * residual raises it towards the +radius position.
     */
    public static double centerOffset(double upwardLength, double radius) {
        if (!Double.isFinite(upwardLength)) upwardLength = 0.0;
        return Mth.clamp(-radius + upwardLength, -radius, radius);
    }
}
