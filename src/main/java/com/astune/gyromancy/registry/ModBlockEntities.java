package com.astune.gyromancy.registry;

import com.astune.gyromancy.Gyromancy;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * BlockEntityType registrations for Gyromancy.
 */
public final class ModBlockEntities {

    private ModBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Gyromancy.MODID);

    // Block entities registered in Phase 4-5:
    //
    // public static final Supplier<BlockEntityType<InkCauldronBlockEntity>> INK_CAULDRON =
    //     BLOCK_ENTITIES.register("ink_cauldron", () -> BlockEntityType.Builder
    //         .of(InkCauldronBlockEntity::new, ModBlocks.INK_CAULDRON.get())
    //         .build(null));
}
