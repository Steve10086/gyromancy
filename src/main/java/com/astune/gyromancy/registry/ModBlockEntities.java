package com.astune.gyromancy.registry;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.block.CrystalBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * BlockEntityType registrations for Gyromancy.
 */
public final class ModBlockEntities {

    private ModBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Gyromancy.MODID);

    /**
     * Data-free render-support block entity shared by every element crystal.
     */
    public static final Supplier<BlockEntityType<CrystalBlockEntity>> CRYSTAL =
            BLOCK_ENTITIES.register("crystal", () -> BlockEntityType.Builder
                    .of(CrystalBlockEntity::new,
                            ModBlocks.FIRE_CRYSTAL.get(),
                            ModBlocks.WATER_CRYSTAL.get(),
                            ModBlocks.WIND_CRYSTAL.get(),
                            ModBlocks.EARTH_CRYSTAL.get(),
                            ModBlocks.LIGHT_CRYSTAL.get(),
                            ModBlocks.DARK_CRYSTAL.get())
                    .build(null));
}
