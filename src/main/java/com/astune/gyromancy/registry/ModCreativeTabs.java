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
                        output.accept(ModItems.COMPASS.get());
                        output.accept(ModItems.INK_BOTTLE.get());
                        output.accept(ModItems.CANVAS.get());
                        output.accept(ModItems.COPPER_RING.get());
                        output.accept(ModItems.COPPER_NUGGET.get());
                        output.accept(ModItems.STAMP.get());
                        output.accept(ModItems.WAND.get());
                        output.accept(ModItems.RUNE_CARVING_TABLE.get());
                        output.accept(ModItems.GUIDEBOOK.get());
                        output.accept(ModItems.FIRE_CRYSTAL.get());
                        output.accept(ModItems.WATER_CRYSTAL.get());
                        output.accept(ModItems.WIND_CRYSTAL.get());
                        output.accept(ModItems.EARTH_CRYSTAL.get());
                        output.accept(ModItems.LIGHT_CRYSTAL.get());
                        output.accept(ModItems.DARK_CRYSTAL.get());
                    })
                    .build());
}
