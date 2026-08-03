package com.astune.gyromancy.wand;

import com.astune.gyromancy.item.WandItem;
import com.astune.gyromancy.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Server-synchronised screen container for a wand's canvas slots. */
public final class WandMenu extends AbstractContainerMenu {
    private final Inventory playerInventory;
    private final InteractionHand hand;
    private final WandContainer wandContainer;
    private final WandLayout layout;

    public WandMenu(int containerId, Inventory inventory, InteractionHand hand) {
        super(ModMenus.WAND.get(), containerId);
        this.playerInventory = inventory;
        this.hand = hand;
        ItemStack wand = inventory.player.getItemInHand(hand);
        this.layout = wand.getItem() instanceof WandItem item
                ? item.layout() : WandLayout.DEFAULT;
        this.wandContainer = new WandContainer(wand, layout);

        int slotIndex = 0;
        for (int slot = 0; slot < layout.slotCount(); slot++) {
            for (int entry = 0; entry < layout.slotCapacity(slot); entry++) {
                addSlot(new CanvasSlot(wandContainer, slotIndex++, 44 + slot * 72,
                        24 + entry * 22));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column,
                        8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
    }

    public static WandMenu fromNetwork(int containerId, Inventory inventory,
                                       RegistryFriendlyByteBuf buffer) {
        return new WandMenu(containerId, inventory, buffer.readEnum(InteractionHand.class));
    }

    public WandContainer wandContainer() {
        return wandContainer;
    }

    public WandLayout layout() {
        return layout;
    }

    public InteractionHand hand() {
        return hand;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.size()) return ItemStack.EMPTY;
        Slot sourceSlot = slots.get(slotIndex);
        if (!sourceSlot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = sourceSlot.getItem();
        ItemStack original = source.copy();
        int playerInventoryStart = wandContainer.getContainerSize();
        if (slotIndex < playerInventoryStart) {
            if (!moveItemStackTo(source, playerInventoryStart, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (source.getItem() instanceof com.astune.gyromancy.item.CanvasItem) {
            if (!moveItemStackTo(source, 0, playerInventoryStart, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }
        if (source.isEmpty()) sourceSlot.setByPlayer(ItemStack.EMPTY);
        else sourceSlot.setChanged();
        sourceSlot.onTake(player, source);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getItemInHand(hand).getItem() instanceof WandItem;
    }

    @Override
    public void removed(Player player) {
        ItemStack carried = getCarried();
        if (!carried.isEmpty()) {
            player.getInventory().placeItemBackInInventory(carried, false);
            setCarried(ItemStack.EMPTY);
        }
        if (!player.level().isClientSide) {
            wandContainer.rebuildSlotSnapshots(layout);
        }
        super.removed(player);
    }

    private static final class CanvasSlot extends Slot {
        private CanvasSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.isEmpty() || stack.getItem() instanceof com.astune.gyromancy.item.CanvasItem;
        }
    }
}
