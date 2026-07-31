package com.astune.gyromancy.element;

import com.astune.gyromancy.Gyromancy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Block-face flow rules for element diffusion.
 *
 * <p>Blocks in {@code gyromancy:blocks_element_flow} seal every face. The
 * face-oriented API leaves room for filters and partial permeability later
 * without changing the tile solver.</p>
 */
public final class ElementFlowBarrier {

    public static final TagKey<Block> BLOCKS_ELEMENT_FLOW = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "blocks_element_flow"));

    private ElementFlowBarrier() {}

    public static boolean isBlockingBlock(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(BLOCKS_ELEMENT_FLOW);
    }

    public static boolean isBlocked(Level level, BlockPos from, Direction direction) {
        BlockPos to = from.relative(direction);
        return isBlockingBlock(level, from) || isBlockingBlock(level, to);
    }
}
