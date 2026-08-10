package com.astune.gyromancy.block;

import com.astune.gyromancy.RuneCarvingTableMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;

/** Workbench-like, non-persistent container for rune engraving. */
public final class RuneCarvingTableBlock extends Block {
    public RuneCarvingTableBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                .strength(2.5F)
                .sound(SoundType.WOOD));
    }

    @Override
    protected InteractionResult useWithoutItem(net.minecraft.world.level.block.state.BlockState state,
                                               Level level,
                                               BlockPos pos,
                                               Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide) {
            player.openMenu(new RuneCarvingTableMenuProvider(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
