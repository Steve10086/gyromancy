package com.astune.gyromancy.registry;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.wand.WandMenu;
import com.astune.gyromancy.rune.RuneCarvingMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Container menu registrations. */
public final class ModMenus {
    private ModMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Gyromancy.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<WandMenu>> WAND =
            MENUS.register("wand", () -> IMenuTypeExtension.create(WandMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<RuneCarvingMenu>> RUNE_CARVING =
            MENUS.register("rune_carving", () -> IMenuTypeExtension.create(RuneCarvingMenu::fromNetwork));
}
