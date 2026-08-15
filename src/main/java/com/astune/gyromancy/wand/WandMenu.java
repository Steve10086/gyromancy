package com.astune.gyromancy.wand;

import com.astune.gyromancy.item.CanvasItem;
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
    private static final int PLAYER_MAIN_SLOT_COUNT = 27;
    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final int PLAYER_SLOT_COUNT = PLAYER_MAIN_SLOT_COUNT + HOTBAR_SLOT_COUNT;
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
    private final int canvasSlotCount;
    private final int playerInventoryStart;
    private final int playerInventoryEnd;

    public WandMenu(int containerId, Inventory inventory, InteractionHand hand) {
        super(ModMenus.WAND.get(), containerId);
        this.playerInventory = inventory;
        this.hand = hand;
        this.wandStack = inventory.player.getItemInHand(hand);
        this.layout = wandStack.getItem() instanceof WandItem item
                ? item.layout() : WandLayout.DEFAULT;
        this.wandContainer = new WandContainer(wandStack, layout);
        this.canvasSlotCount = wandContainer.getContainerSize();
        this.playerInventoryStart = canvasSlotCount;

        int slotIndex = canvasSlotStart();
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
        this.playerInventoryEnd = slots.size();
        if (playerInventoryEnd - playerInventoryStart != PLAYER_SLOT_COUNT) {
            throw new IllegalStateException("Wand menu player inventory slot count changed");
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

    /**
     * The menu index at which the wand's canvas slots begin.
     *
     * <p>Keeping this range explicit is important because all vanilla
     * container shortcuts eventually operate on menu slot indexes rather
     * than on the backing inventory's indexes.</p>
     */
    public static int canvasSlotStart() {
        return 0;
    }

    public int canvasSlotCount() {
        return canvasSlotCount;
    }

    public int playerInventoryStart() {
        return playerInventoryStart;
    }

    public int playerInventoryEnd() {
        return playerInventoryEnd;
    }

    public int playerMainInventoryStart() {
        return playerInventoryStart;
    }

    public int hotbarStart() {
        return playerInventoryStart + PLAYER_MAIN_SLOT_COUNT;
    }

    public boolean isPlayerMainInventorySlot(int menuSlot) {
        return menuSlot >= playerMainInventoryStart()
                && menuSlot < hotbarStart();
    }

    public boolean isHotbarSlot(int menuSlot) {
        return menuSlot >= hotbarStart() && menuSlot < playerInventoryEnd;
    }

    public boolean isCanvasSlot(int menuSlot) {
        return menuSlot >= canvasSlotStart() && menuSlot < playerInventoryStart;
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
        if (slotIndex < 0 || slotIndex >= playerInventoryEnd) return ItemStack.EMPTY;
        Slot sourceSlot = slots.get(slotIndex);
        if (!sourceSlot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = sourceSlot.getItem();
        ItemStack original = source.copy();

        boolean moved;
        if (isCanvasSlot(slotIndex)) {
            // Match the vanilla chest/inventory routing: merge into existing
            // stacks first, then search the player inventory backwards so the
            // hotbar is preferred for a newly moved stack.
            moved = moveItemStackTo(source, playerInventoryStart,
                    playerInventoryEnd, true);
        } else if (isPlayerMainInventorySlot(slotIndex)
                || isHotbarSlot(slotIndex)) {
            // Canvas items get the container's preferred destination first.
            // If that area is full, fall back to the same main-inventory /
            // hotbar routing used by InventoryMenu.
            moved = source.getItem() instanceof CanvasItem
                    && moveItemStackTo(source, canvasSlotStart(),
                    canvasSlotCount, false);
            if (!moved) {
                moved = isPlayerMainInventorySlot(slotIndex)
                        ? moveItemStackTo(source, hotbarStart(),
                        playerInventoryEnd, false)
                        : moveItemStackTo(source, playerMainInventoryStart(),
                        hotbarStart(), false);
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (!moved) return ItemStack.EMPTY;
        if (source.isEmpty()) {
            sourceSlot.setByPlayer(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }
        if (source.getCount() == original.getCount()) return ItemStack.EMPTY;
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
            return stack.isEmpty() || stack.getItem() instanceof CanvasItem;
        }
    }
}
