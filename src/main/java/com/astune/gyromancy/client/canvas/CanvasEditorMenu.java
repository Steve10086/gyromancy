package com.astune.gyromancy.client.canvas;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Client-local container used by the canvas editor.
 *
 * <p>It deliberately has no registered menu type and sends no vanilla
 * container-click packets. The inherited menu logic still provides pickup,
 * split, quick-craft, double-click and hotbar swap behavior.
 */
final class CanvasEditorMenu extends AbstractContainerMenu {
    static final int MAIN_SLOT_COUNT = 27;
    static final int TOTAL_SLOT_COUNT = Inventory.INVENTORY_SIZE;
    private static final int SLOT_X = 3;
    private static final int SLOT_SPACING_X = 20;
    private static final int ROW_SPACING_Y = 22;
    private static final int FIRST_MAIN_ROW_Y = -63;
    private static final int HOTBAR_Y = 3;

    private final Inventory inventory;
    private boolean expanded;
    private boolean interactionFinished;

    CanvasEditorMenu(Inventory inventory) {
        super(null, -1);
        this.inventory = inventory;

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int inventorySlot = 9 + row * 9 + column;
                addSlot(new MainInventorySlot(
                        inventory,
                        inventorySlot,
                        SLOT_X + column * SLOT_SPACING_X,
                        FIRST_MAIN_ROW_Y + row * ROW_SPACING_Y,
                        this));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(
                    inventory,
                    column,
                    SLOT_X + column * SLOT_SPACING_X,
                    HOTBAR_Y));
        }
    }

    Inventory inventory() {
        return inventory;
    }

    void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    boolean isExpanded() {
        return expanded;
    }

    void finishInteraction(Player player) {
        if (interactionFinished) return;
        interactionFinished = true;
        ItemStack carried = getCarried();
        if (!carried.isEmpty()) {
            inventory.placeItemBackInInventory(carried, false);
            setCarried(ItemStack.EMPTY);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (!expanded || slotIndex < 0 || slotIndex >= slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack original = source.copy();
        boolean moved = slotIndex < MAIN_SLOT_COUNT
                ? moveItemStackTo(source, MAIN_SLOT_COUNT, TOTAL_SLOT_COUNT, false)
                : moveItemStackTo(source, 0, MAIN_SLOT_COUNT, false);
        if (!moved) return ItemStack.EMPTY;

        if (source.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY, original);
        } else {
            slot.setChanged();
        }
        if (source.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, source);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        finishInteraction(player);
    }

    private static final class MainInventorySlot extends Slot {
        private final CanvasEditorMenu menu;

        private MainInventorySlot(Inventory inventory,
                                  int slot,
                                  int x,
                                  int y,
                                  CanvasEditorMenu menu) {
            super(inventory, slot, x, y);
            this.menu = menu;
        }

        @Override
        public boolean isActive() {
            return menu.isExpanded();
        }
    }
}
