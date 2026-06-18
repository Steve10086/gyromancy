package com.astune.gyromancy.registry;

import com.astune.gyromancy.Gyromancy;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Creative tab registrations for Gyromancy.
 */
public final class ModCreativeTabs {

    private ModCreativeTabs() {}

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Gyromancy.MODID);

    /** Main Gyromancy tab for all magical items and blocks */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> GYROMANCY_TAB =
            CREATIVE_TABS.register("gyromancy", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.gyromancy"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> Items.BOOK.getDefaultInstance()) // Placeholder — will use pen/ink icon
                    .displayItems((parameters, output) -> {
                        // Items added here in Phase 4+
                    })
                    .build());
}
