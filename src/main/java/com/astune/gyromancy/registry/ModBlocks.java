package com.astune.gyromancy.registry;

import com.astune.gyromancy.Gyromancy;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block registrations for Gyromancy.
 * Canvas blocks, ink cauldrons, and other magical apparatus blocks.
 */
public final class ModBlocks {

    private ModBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Gyromancy.MODID);

    // Blocks will be registered here in Phase 4-5:
    //
    // public static final DeferredBlock<Block> INK_CAULDRON = BLOCKS.registerSimpleBlock(
    //     "ink_cauldron", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK));
}
