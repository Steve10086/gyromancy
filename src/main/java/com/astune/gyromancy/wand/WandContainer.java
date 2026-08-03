package com.astune.gyromancy.wand;

import com.astune.gyromancy.item.CanvasItem;
import com.astune.gyromancy.registry.ModDataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** A menu-facing inventory that writes every change back to the held wand. */
final class WandContainer extends SimpleContainer {
    private final ItemStack wandStack;
    private boolean initialized;

    WandContainer(ItemStack wandStack, WandLayout layout) {
        super(layout.totalCapacity());
        this.wandStack = wandStack;
        WandContents contents = wandStack.getOrDefault(
                ModDataComponents.WAND_CONTENTS.get(), WandContents.EMPTY)
                .withSize(getContainerSize());
        for (int slot = 0; slot < getContainerSize(); slot++) {
            super.setItem(slot, contents.get(slot));
        }
        initialized = true;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.isEmpty() || stack.getItem() instanceof CanvasItem;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        super.setItem(slot, stack);
        saveToWand();
    }

    @Override
    public void setChanged() {
        super.setChanged();
        saveToWand();
    }

    private void saveToWand() {
        if (!initialized) return;
        WandContents contents = WandContents.EMPTY;
        for (int slot = 0; slot < getContainerSize(); slot++) {
            contents = contents.with(slot, getItem(slot));
        }
        wandStack.set(ModDataComponents.WAND_CONTENTS.get(), contents);
    }

    void rebuildSlotSnapshots(WandLayout layout) {
        WandContents contents = wandStack.getOrDefault(
                ModDataComponents.WAND_CONTENTS.get(), WandContents.EMPTY);
        WandSlotSnapshots previous = wandStack.getOrDefault(
                ModDataComponents.WAND_SLOT_SNAPSHOTS.get(), WandSlotSnapshots.EMPTY);
        WandSlotSnapshots next = WandSlotCompiler.refresh(previous, contents, layout);
        if (!next.equals(previous)) {
            wandStack.set(ModDataComponents.WAND_SLOT_SNAPSHOTS.get(), next);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return wandStack.getItem() instanceof com.astune.gyromancy.item.WandItem;
    }
}
