package com.astune.gyromancy.registry;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.item.DebugBrushItem;
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
}
