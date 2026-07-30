package com.astune.gyromancy.registry;

import com.astune.gyromancy.Gyromancy;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
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
                    .icon(() -> ModItems.DEBUG_BRUSH.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.DEBUG_BRUSH.get());
                        output.accept(ModItems.PEN.get());
                        output.accept(ModItems.INK_BOTTLE.get());
                        output.accept(ModItems.CANVAS.get());
                        output.accept(ModItems.STAMP.get());
                    })
                    .build());
}
