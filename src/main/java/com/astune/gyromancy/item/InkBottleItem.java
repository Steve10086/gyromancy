package com.astune.gyromancy.item;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.registry.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Holds ink for use by a {@link PenItem} in the offhand.
 *
 * <p>The {@code INK_TYPE} component links to a registered {@code InkType}.
 * {@code INK_REMAINING} tracks remaining charges — each paint action consumes
 * one charge. When depleted, the item breaks (or can be refilled later).
 *
 * <p>To add new ink types, register them in {@code InkRegistry} and assign
 * the matching {@code INK_TYPE} component. No code changes needed here.
 */
public class InkBottleItem extends Item {

    /** Default max ink charges for a full bottle */
    public static final int MAX_INK = 64;

    public InkBottleItem() {
        super(new Properties()
                .stacksTo(1)
                .component(ModDataComponents.INK_TYPE.get(),
                        ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "mana_ink"))
                .component(ModDataComponents.INK_REMAINING.get(), MAX_INK));
    }

    // ═══════════════════════════════════════════════════════════════
    // Durability bar via INK_REMAINING component
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean isBarVisible(ItemStack stack) {
        int remaining = stack.getOrDefault(ModDataComponents.INK_REMAINING.get(), MAX_INK);
        return remaining < MAX_INK;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int remaining = stack.getOrDefault(ModDataComponents.INK_REMAINING.get(), 0);
        return Math.round(13.0f * remaining / MAX_INK);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x44BBFF; // cyan-ish ink bar
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation inkType = stack.get(ModDataComponents.INK_TYPE.get());
        if (inkType != null && !inkType.getPath().equals("air")) {
            return Component.translatable(
                    "item.gyromancy.ink_bottle.filled",
                    Component.translatable("ink.gyromancy." + inkType.getPath()));
        }
        return super.getName(stack);
    }
}
