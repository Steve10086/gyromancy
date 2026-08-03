package com.astune.gyromancy.item;

import com.astune.gyromancy.registry.ModDataComponents;
import com.astune.gyromancy.wand.WandContents;
import com.astune.gyromancy.wand.WandLayout;
import com.astune.gyromancy.wand.WandMenu;
import com.astune.gyromancy.wand.WandProjectionService;
import com.astune.gyromancy.wand.WandSlotSnapshots;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/** A reusable projector for cached canvas drawings and magic arrays. */
public class WandItem extends Item {
    private final WandLayout layout;

    public WandItem() {
        this(WandLayout.DEFAULT);
    }

    protected WandItem(WandLayout layout) {
        super(new Properties().stacksTo(1)
                .component(ModDataComponents.WAND_CONTENTS.get(), WandContents.EMPTY)
                .component(ModDataComponents.WAND_SLOT_SNAPSHOTS.get(), WandSlotSnapshots.EMPTY));
        this.layout = layout;
    }

    public WandLayout layout() {
        return layout;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                player.openMenu(new WandMenuProvider(hand));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        player.startUsingItem(hand);
        if (!level.isClientSide) {
            WandProjectionService.project(level, player, stack, layout);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72_000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level,
                             LivingEntity livingEntity, int timeLeft) {
        if (!level.isClientSide && livingEntity instanceof Player player) {
            WandProjectionService.stop(level, player);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.gyromancy.wand.slots", layout.slotCount(),
                layout.totalCapacity()));
        tooltip.add(Component.translatable("item.gyromancy.wand.help"));
    }

    private static final class WandMenuProvider implements MenuProvider {
        private final InteractionHand hand;

        private WandMenuProvider(InteractionHand hand) {
            this.hand = hand;
        }

        @Override
        public Component getDisplayName() {
            return Component.translatable("screen.gyromancy.wand");
        }

        @Override
        public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                int id, net.minecraft.world.entity.player.Inventory inventory, Player player) {
            return new WandMenu(id, inventory, hand);
        }

        @Override
        public void writeClientSideData(net.minecraft.world.inventory.AbstractContainerMenu menu,
                                        net.minecraft.network.RegistryFriendlyByteBuf buffer) {
            buffer.writeEnum(hand);
        }
    }
}
