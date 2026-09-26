package com.astune.gyromancy.registry;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.block.CrystalBlock;
import com.astune.gyromancy.block.RuneCarvingTableBlock;
import com.astune.gyromancy.block.SilverLeavesBlock;
import com.astune.gyromancy.block.SilverLogBlock;
import com.astune.gyromancy.block.WitheredSilverLeavesBlock;
import com.astune.gyromancy.worldgen.SilverTreeGrower;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
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

    public static final DeferredBlock<CrystalBlock> LIGHT_CRYSTAL =
            BLOCKS.register("light_crystal", CrystalBlock::light);

    public static final DeferredBlock<CrystalBlock> DARK_CRYSTAL =
            BLOCKS.register("dark_crystal", CrystalBlock::dark);

    /** River silver tree log; carries tip and natural-growth states. */
    public static final DeferredBlock<SilverLogBlock> SILVER_LOG =
            BLOCKS.register("silver_log", SilverLogBlock::new);

    /** Silver tree planks; standard wood-family building block. */
    public static final DeferredBlock<Block> SILVER_PLANKS =
            BLOCKS.register("silver_planks", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(2.0F, 3.0F)
                    .sound(SoundType.WOOD)
                    .ignitedByLava()));

    /** Living silver leaves that wither without a non-leaf silver log nearby. */
    public static final DeferredBlock<SilverLeavesBlock> SILVER_LEAVES =
            BLOCKS.register("silver_leaves", SilverLeavesBlock::new);

    /** Withered silver leaves; always drop a silver branch. */
    public static final DeferredBlock<WitheredSilverLeavesBlock> WITHERED_SILVER_LEAVES =
            BLOCKS.register("withered_silver_leaves", WitheredSilverLeavesBlock::new);

    /** Sapling that grows the silver tree through {@link SilverTreeGrower}. */
    public static final DeferredBlock<SaplingBlock> SILVER_SAPLING =
            BLOCKS.register("silver_sapling", () -> new SaplingBlock(
                    SilverTreeGrower.SILVER,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.PLANT)
                            .noCollission()
                            .randomTicks()
                            .instabreak()
                            .sound(SoundType.GRASS)
                            .pushReaction(PushReaction.DESTROY)));
}
