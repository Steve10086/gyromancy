package com.astune.gyromancy.registry;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.block.CrystalBlock;
import com.astune.gyromancy.block.RuneCarvingTableBlock;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block registrations for Gyromancy.
 * Canvas blocks, ink cauldrons, and other magical apparatus blocks.
 */
public final class ModBlocks {

    private ModBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Gyromancy.MODID);

    /** Workbench-like table which owns no persistent inventory. */
    public static final DeferredBlock<RuneCarvingTableBlock> RUNE_CARVING_TABLE =
            BLOCKS.register("rune_carving_table", RuneCarvingTableBlock::new);

    /** Element crystals grown by the matching crystal generation payloads. */
    public static final DeferredBlock<CrystalBlock> FIRE_CRYSTAL =
            BLOCKS.register("fire_crystal", CrystalBlock::fire);

    public static final DeferredBlock<CrystalBlock> WATER_CRYSTAL =
            BLOCKS.register("water_crystal", CrystalBlock::water);

    public static final DeferredBlock<CrystalBlock> WIND_CRYSTAL =
            BLOCKS.register("wind_crystal", CrystalBlock::wind);

    public static final DeferredBlock<CrystalBlock> EARTH_CRYSTAL =
            BLOCKS.register("earth_crystal", CrystalBlock::earth);
}
