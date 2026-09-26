package com.astune.gyromancy.block;

import com.astune.gyromancy.registry.ModBlocks;
import com.astune.gyromancy.worldgen.SilverTreeLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Withered silver leaves.
 *
 * <p>Mined blocks always drop a silver branch. A random tick revives them when
 * a natural silver leaf and a non-leaf silver log both stand within
 * {@link SilverTreeLogic#LEAF_SUPPORT_RADIUS} blocks.</p>
 */
public class WitheredSilverLeavesBlock extends LeavesBlock {

    public WitheredSilverLeavesBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.PLANT)
                .strength(0.2F)
                .randomTicks()
                .sound(SoundType.GRASS)
                .noOcclusion()
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false)
                .ignitedByLava()
                .pushReaction(PushReaction.DESTROY)
                .isRedstoneConductor((state, level, pos) -> false));
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return !state.getValue(PERSISTENT);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(PERSISTENT)) {
            return;
        }
        if (SilverTreeLogic.hasNativeSilverLeaves(level, pos)
                && SilverTreeLogic.hasSupportingLog(level, pos)) {
            level.setBlock(pos, ModBlocks.SILVER_LEAVES.get().defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
