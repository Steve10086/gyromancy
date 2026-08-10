package com.astune.gyromancy.rune;

import com.astune.gyromancy.api.canvas.Carvable;
import com.astune.gyromancy.registry.ModBlocks;
import com.astune.gyromancy.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-synchronised table menu. The carving slot is managed by
 * {@link ItemCombinerMenu}, so it gets the same container callbacks, click
 * handling, quick-move behaviour and close-time item return as an anvil.
 *
 * <p>The result slot remains empty because carving updates the input item in
 * place; it exists only because {@code ItemCombinerMenu} requires a result
 * definition.</p>
 */
public final class RuneCarvingMenu extends ItemCombinerMenu {
    public static final int INPUT_SLOT = 0;
    public static final int RESULT_SLOT = 1;
    public static final int PLAYER_SLOT_START = RESULT_SLOT + 1;

    private final BlockPos tablePos;
    private boolean expanded;
    private int lastCarvingSequence = -1;

    public RuneCarvingMenu(int containerId, Inventory inventory, BlockPos tablePos) {
        this(containerId, inventory, tablePos, ContainerLevelAccess.NULL);
    }

    public RuneCarvingMenu(int containerId,
                           Inventory inventory,
                           BlockPos tablePos,
                           ContainerLevelAccess access) {
        super(ModMenus.RUNE_CARVING.get(), containerId, inventory, access);
        this.tablePos = tablePos;
        replaceInventorySlots(inventory);
    }

    public static RuneCarvingMenu fromNetwork(int containerId,
                                              Inventory inventory,
                                              RegistryFriendlyByteBuf buffer) {
        return new RuneCarvingMenu(containerId, inventory, buffer.readBlockPos());
    }

    @Override
    protected ItemCombinerMenuSlotDefinition createInputSlotDefinitions() {
        return ItemCombinerMenuSlotDefinition.create()
                .withSlot(INPUT_SLOT, -100, -100,
                        stack -> stack.getItem() instanceof Carvable)
                .withResultSlot(RESULT_SLOT, -101, -101)
                .build();
    }

    @Override
    protected boolean isValidBlock(BlockState state) {
        return state.is(ModBlocks.RUNE_CARVING_TABLE.get());
    }

    @Override
    protected boolean mayPickup(Player player, boolean hasStack) {
        return false;
    }

    @Override
    protected void onTake(Player player, ItemStack stack) {
        // The carving editor writes to the input item in place. There is no
        // output item to consume from the compatibility result slot.
    }

    @Override
    public void createResult() {
        // The table has no result slot; carving is submitted live by packet.
        resultSlots.setItem(0, ItemStack.EMPTY);
    }

    public BlockPos tablePos() {
        return tablePos;
    }

    public ItemStack carvingStack() {
        return inputSlots.getItem(INPUT_SLOT);
    }

    public int menuId() {
        return containerId;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
        for (int index = PLAYER_SLOT_START; index < slots.size(); index++) {
            Slot slot = slots.get(index);
            if (slot instanceof MainInventorySlot mainInventorySlot) {
                mainInventorySlot.setActive(expanded);
            }
        }
    }

    public boolean acceptCarvingSequence(int sequence) {
        if (sequence <= lastCarvingSequence) return false;
        lastCarvingSequence = sequence;
        return true;
    }

    @Override
    protected boolean canMoveIntoInputSlots(ItemStack stack) {
        // ItemCombinerMenu uses this hook before attempting a shift-click from
        // either player-inventory section. Returning false lets ordinary
        // items follow the parent's normal main-inventory/hotbar routing
        // instead of being rejected merely because this table has a filtered
        // input slot.
        return stack.getItem() instanceof Carvable;
    }

    /**
     * ItemCombinerMenu creates the inventory slots with the vanilla positions.
     * Replace only those slot objects so the canvas screen can retain its
     * responsive 20x22 inventory layout and collapsible main inventory while
     * keeping the parent menu's slot indexes and state synchronization.
     */
    private void replaceInventorySlots(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int menuSlot = PLAYER_SLOT_START + row * 9 + column;
                MainInventorySlot replacement = new MainInventorySlot(inventory,
                        9 + row * 9 + column,
                        3 + column * 20,
                        -63 + row * 22);
                replacement.index = menuSlot;
                slots.set(menuSlot, replacement);
            }
        }
        for (int column = 0; column < 9; column++) {
            int menuSlot = PLAYER_SLOT_START + 27 + column;
            Slot replacement = new Slot(inventory, column,
                    3 + column * 20, 3);
            replacement.index = menuSlot;
            slots.set(menuSlot, replacement);
        }
    }

    private final class MainInventorySlot extends Slot {
        private boolean active;

        private MainInventorySlot(Inventory inventory, int slot, int x, int y) {
            super(inventory, slot, x, y);
        }

        private void setActive(boolean active) {
            this.active = active;
        }

        @Override
        public boolean isActive() {
            return active;
        }
    }
}
