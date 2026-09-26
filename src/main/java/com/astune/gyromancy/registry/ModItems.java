package com.astune.gyromancy.registry;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.item.CompassItem;
import com.astune.gyromancy.item.DebugBrushItem;
import com.astune.gyromancy.item.GuidebookItem;
import com.astune.gyromancy.item.InkBottleItem;
import com.astune.gyromancy.item.PenItem;
import com.astune.gyromancy.item.CanvasItem;
import com.astune.gyromancy.item.CopperRingItem;
import com.astune.gyromancy.item.StampItem;
import com.astune.gyromancy.item.WandItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Item registrations for Gyromancy.
 * Pens, ink bottles, symbol scrolls, and other magical items.
 */
public final class ModItems {

    private ModItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Gyromancy.MODID);

    /** Debug brush that paints single-pixel mana dots for symbol testing */
    public static final DeferredItem<DebugBrushItem> DEBUG_BRUSH = ITEMS.register("debug_brush",
            DebugBrushItem::new);

    /** Main-hand pen that reads offhand ink and paints on canvas blocks */
    public static final DeferredItem<PenItem> PEN = ITEMS.register("pen", PenItem::new);

    /** Compass that paints a radius-controlled circle or arc. */
    public static final DeferredItem<CompassItem> COMPASS = ITEMS.register("compass", CompassItem::new);

    /** Offhand ink bottle providing color, mana, and effect layers */
    public static final DeferredItem<InkBottleItem> INK_BOTTLE = ITEMS.register("ink_bottle",
            InkBottleItem::new);

    /** Portable entity-backed drawing surface. */
    public static final DeferredItem<CanvasItem> CANVAS = ITEMS.register("canvas",
            CanvasItem::new);

    /** Carvable 64x64 copper ring used as an intermediate crafting material. */
    public static final DeferredItem<CopperRingItem> COPPER_RING = ITEMS.register("copper_ring",
            CopperRingItem::new);

    /** Copper nugget used to craft copper rings. */
    public static final DeferredItem<Item> COPPER_NUGGET = ITEMS.register("copper_nugget",
            () -> new Item(new Item.Properties()));

    /** Reusable painting pattern captured from a Painter canvas face. */
    public static final DeferredItem<StampItem> STAMP = ITEMS.register("stamp",
            StampItem::new);

    /** Default two-plane wand: two canvas slots at one and one-and-a-half blocks. */
    public static final DeferredItem<WandItem> WAND = ITEMS.register("wand",
            WandItem::new);

    /** Block item for the non-persistent rune carving table. */
    public static final DeferredItem<BlockItem> RUNE_CARVING_TABLE = ITEMS.register(
            "rune_carving_table",
            () -> new BlockItem(ModBlocks.RUNE_CARVING_TABLE.get(), new Item.Properties()));

    /** In-game manual for the basic Gyromancy workflow. */
    public static final DeferredItem<GuidebookItem> GUIDEBOOK = ITEMS.register(
            "guidebook", GuidebookItem::new);

    /** Unplaceable crystal drops harvested from grown element crystal blocks. */
    public static final DeferredItem<Item> FIRE_CRYSTAL = ITEMS.register(
            "fire_crystal", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> WATER_CRYSTAL = ITEMS.register(
            "water_crystal", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> WIND_CRYSTAL = ITEMS.register(
            "wind_crystal", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> EARTH_CRYSTAL = ITEMS.register(
            "earth_crystal", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> LIGHT_CRYSTAL = ITEMS.register(
            "light_crystal", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> DARK_CRYSTAL = ITEMS.register(
            "dark_crystal", () -> new Item(new Item.Properties()));

    /** Placeable silver tree log. */
    public static final DeferredItem<BlockItem> SILVER_LOG = ITEMS.register(
            "silver_log", () -> new BlockItem(ModBlocks.SILVER_LOG.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> SILVER_PLANKS = ITEMS.register(
            "silver_planks", () -> new BlockItem(ModBlocks.SILVER_PLANKS.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> SILVER_LEAVES = ITEMS.register(
            "silver_leaves", () -> new BlockItem(ModBlocks.SILVER_LEAVES.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> WITHERED_SILVER_LEAVES = ITEMS.register(
            "withered_silver_leaves",
            () -> new BlockItem(ModBlocks.WITHERED_SILVER_LEAVES.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> SILVER_SAPLING = ITEMS.register(
            "silver_sapling", () -> new BlockItem(ModBlocks.SILVER_SAPLING.get(), new Item.Properties()));

    /** Drop of withered silver leaves; grinding input for silver powder. */
    public static final DeferredItem<Item> SILVER_BRANCH = ITEMS.register(
            "silver_branch", () -> new Item(new Item.Properties()));

    /** Fixed durability of the crafting mortar. */
    public static final int MORTAR_DURABILITY = 256;

    /** Crafting mortar used to grind silver materials; damaged instead of consumed. */
    public static final DeferredItem<Item> MORTAR = ITEMS.register(
            "mortar", () -> new Item(new Item.Properties().durability(MORTAR_DURABILITY)));

    /** Ground silver tree product; no further use yet. */
    public static final DeferredItem<Item> SILVER_POWDER = ITEMS.register(
            "silver_powder", () -> new Item(new Item.Properties()));

    /** Mortar-ground powders; no further use yet. */
    public static final DeferredItem<Item> FEATHER_POWDER = ITEMS.register(
            "feather_powder", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> DEEPSLATE_POWDER = ITEMS.register(
            "deepslate_powder", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> FISH_POWDER = ITEMS.register(
            "fish_powder", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> CHARCOAL_POWDER = ITEMS.register(
            "charcoal_powder", () -> new Item(new Item.Properties()));

    /** Mortar-ground element crystal powders; ink mixing inputs. */
    public static final DeferredItem<Item> FIRE_CRYSTAL_POWDER = ITEMS.register(
            "fire_crystal_powder", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> WATER_CRYSTAL_POWDER = ITEMS.register(
            "water_crystal_powder", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> WIND_CRYSTAL_POWDER = ITEMS.register(
            "wind_crystal_powder", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> EARTH_CRYSTAL_POWDER = ITEMS.register(
            "earth_crystal_powder", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> LIGHT_CRYSTAL_POWDER = ITEMS.register(
            "light_crystal_powder", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> DARK_CRYSTAL_POWDER = ITEMS.register(
            "dark_crystal_powder", () -> new Item(new Item.Properties()));
}
