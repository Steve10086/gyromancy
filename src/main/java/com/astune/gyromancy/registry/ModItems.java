package com.astune.gyromancy.registry;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.item.CompassItem;
import com.astune.gyromancy.item.DebugBrushItem;
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
}
