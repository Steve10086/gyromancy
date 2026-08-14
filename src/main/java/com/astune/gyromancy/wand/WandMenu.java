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
    public static final int SLOT_SIZE = 18;
    public static final int SCREEN_WIDTH = 176;
    public static final int SCREEN_HEIGHT = 227;
    public static final int INVENTORY_LEFT = 8;
    public static final int INVENTORY_TOP = 140;

    // The first four positions are reserved for the left/right wand slot
    // sprites. They form two inward-facing pairs around the central display.
    private static final int[][] CONFIG_SLOT_POSITIONS = {
            {32, 32}, {42, 52}, {128, 68}, {118, 88}
    };

    private final Inventory playerInventory;
    private final InteractionHand hand;
    private final ItemStack wandStack;
    private final WandContainer wandContainer;
    private final WandLayout layout;

    public WandMenu(int containerId, Inventory inventory, InteractionHand hand) {
        super(ModMenus.WAND.get(), containerId);
        this.playerInventory = inventory;
        this.hand = hand;
        this.wandStack = inventory.player.getItemInHand(hand);
        this.layout = wandStack.getItem() instanceof WandItem item
                ? item.layout() : WandLayout.DEFAULT;
        this.wandContainer = new WandContainer(wandStack, layout);

        int slotIndex = 0;
        for (int slot = 0; slot < layout.slotCount(); slot++) {
            for (int entry = 0; entry < layout.slotCapacity(slot); entry++) {
                addSlot(new CanvasSlot(wandContainer, slotIndex++, canvasSlotX(layout, slot, entry),
                        canvasSlotY(layout, slot, entry)));
            }
        }

        int inventoryX = inventoryLeft(layout);
        int inventoryY = inventoryTop(layout);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column,
                        inventoryX + column * 18, inventoryY + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, inventoryX + column * 18, inventoryY + 58));
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

    public ItemStack wandStack() {
        return wandStack;
    }

    public InteractionHand hand() {
        return hand;
    }

    public static int screenWidth(WandLayout layout) {
        return SCREEN_WIDTH;
    }

    public static int upperHeight(WandLayout layout) {
        return INVENTORY_TOP;
    }

    public static int inventoryTop(WandLayout layout) {
        return INVENTORY_TOP;
    }

    public static int screenHeight(WandLayout layout) {
        return SCREEN_HEIGHT;
    }

    public static int inventoryLeft(WandLayout layout) {
        return INVENTORY_LEFT;
    }

    public static int canvasSlotX(WandLayout layout, int slot, int entry) {
        int position = positionIndex(layout, slot, entry);
        return position < CONFIG_SLOT_POSITIONS.length
                ? CONFIG_SLOT_POSITIONS[position][0] : fallbackSlotX(slot);
    }

    public static int canvasSlotY(WandLayout layout, int slot, int entry) {
        int position = positionIndex(layout, slot, entry);
        return position < CONFIG_SLOT_POSITIONS.length
                ? CONFIG_SLOT_POSITIONS[position][1] : fallbackSlotY(entry);
    }

    private static int positionIndex(WandLayout layout, int slot, int entry) {
        int index = entry;
        for (int previous = 0; previous < slot; previous++) {
            index += layout.slotCapacity(previous);
        }
        return index;
    }

    private static int fallbackSlotX(int slot) {
        return (slot & 1) == 0 ? 32 : 128;
    }

    private static int fallbackSlotY(int entry) {
        return 32 + entry * 22;
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
