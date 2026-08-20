package com.astune.gyromancy.item;

import com.astune.gyromancy.client.guidebook.GuidebookClientHooks;
import com.astune.gyromancy.guidebook.GuidebookCatalog;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.level.Level;

/**
 * A native written book whose pages are generated from the structured guidebook catalog.
 *
 * <p>The small client hook is only needed because vanilla's server-side open-book packet
 * recognizes the vanilla written-book item, not arbitrary subclasses.</p>
 */
public final class GuidebookItem extends WrittenBookItem {

    public GuidebookItem() {
        super(new Item.Properties().stacksTo(1)
                .component(DataComponents.WRITTEN_BOOK_CONTENT, GuidebookCatalog.createWrittenBookContent()));
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.gyromancy.guidebook");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            GuidebookClientHooks.open(stack);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
