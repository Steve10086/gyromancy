package com.astune.gyromancy.registry;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.item.DebugBrushItem;
import com.astune.gyromancy.item.InkBottleItem;
import com.astune.gyromancy.item.PenItem;
import com.astune.gyromancy.item.CanvasItem;
import com.astune.gyromancy.item.StampItem;
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

    /** Offhand ink bottle providing color, mana, and effect layers */
    public static final DeferredItem<InkBottleItem> INK_BOTTLE = ITEMS.register("ink_bottle",
            InkBottleItem::new);

    /** Portable entity-backed drawing surface. */
    public static final DeferredItem<CanvasItem> CANVAS = ITEMS.register("canvas",
            CanvasItem::new);

    /** Reusable painting pattern captured from a Painter canvas face. */
    public static final DeferredItem<StampItem> STAMP = ITEMS.register("stamp",
            StampItem::new);
}
