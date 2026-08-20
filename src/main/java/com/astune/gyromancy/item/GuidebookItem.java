package com.astune.gyromancy.item;

import net.favouriteless.modopedia.common.MBookItem;
import net.favouriteless.modopedia.common.init.MDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The crafted Gyromancy manual item backed by Modopedia's native book screen.
 */
public final class GuidebookItem extends MBookItem {

    private static final ResourceLocation BOOK_ID =
            ResourceLocation.fromNamespaceAndPath("gyromancy", "guidebook");

    public GuidebookItem() {
        super();
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.gyromancy.guidebook");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.has(MDataComponents.BOOK.get())) {
            stack.set(MDataComponents.BOOK.get(), BOOK_ID);
        }
        return super.use(level, player, hand);
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        stack.set(MDataComponents.BOOK.get(), BOOK_ID);
        return stack;
    }
}
