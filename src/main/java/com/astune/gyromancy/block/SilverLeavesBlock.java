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
 * Living silver leaves.
 *
 * <p>Unlike vanilla leaves they never drop from missing logs; a random tick
 * withers them when no non-leaf silver log supports them within
 * {@link SilverTreeLogic#LEAF_SUPPORT_RADIUS} blocks. Player-placed leaves set
 * {@code persistent} through vanilla's placement logic and are excluded from
 * every conversion rule.</p>
 */
public class SilverLeavesBlock extends LeavesBlock {

    public SilverLeavesBlock() {
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
        if (!SilverTreeLogic.hasSupportingLog(level, pos)) {
            level.setBlock(pos, ModBlocks.WITHERED_SILVER_LEAVES.get().defaultBlockState(),
                    Block.UPDATE_ALL);
        }
    }
}
