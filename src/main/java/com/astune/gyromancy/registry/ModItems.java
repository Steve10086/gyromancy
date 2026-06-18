package com.astune.gyromancy.registry;

import com.astune.gyromancy.Gyromancy;
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

    // Items will be registered here in Phase 4:
    //
    // public static final DeferredItem<PenItem> WOODEN_PEN = ITEMS.register("wooden_pen",
    //     () -> new PenItem(new Item.Properties().stacksTo(1)));
    //
    // public static final DeferredItem<InkBottleItem> INK_BOTTLE = ITEMS.register("ink_bottle",
    //     () -> new InkBottleItem(new Item.Properties().stacksTo(16)));
}
