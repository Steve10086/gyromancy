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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Workbench-like, non-persistent container for rune engraving. */
public final class RuneCarvingTableBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
            // Tabletop and its narrow front edge.
            Block.box(0.0D, 8.0D, 0.5D, 15.0D, 10.0D, 15.5D),
            Block.box(0.0D, 10.0D, 0.5D, 2.0D, 10.5D, 15.5D),
            Block.box(7.0D, 10.0D, 1.5D, 14.0D, 10.25D, 12.5D),
            // Four legs.
            Block.box(1.0D, 0.0D, 1.5D, 3.0D, 8.0D, 3.5D),
            Block.box(1.0D, 0.0D, 12.5D, 3.0D, 8.0D, 14.5D),
            Block.box(12.0D, 0.0D, 1.5D, 14.0D, 8.0D, 3.5D),
            Block.box(12.0D, 0.0D, 12.5D, 14.0D, 8.0D, 14.5D)
    );

    public RuneCarvingTableBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                .strength(2.5F)
                .sound(SoundType.WOOD));
    }

    @Override
    protected VoxelShape getShape(net.minecraft.world.level.block.state.BlockState state,
                                  net.minecraft.world.level.BlockGetter level,
                                  BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
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
