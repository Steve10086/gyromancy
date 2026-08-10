package com.astune.gyromancy;

import com.astune.gyromancy.rune.RuneCarvingMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;

/** Menu provider used by the rune carving table block. */
public final class RuneCarvingTableMenuProvider implements MenuProvider {
    private final BlockPos pos;

    public RuneCarvingTableMenuProvider(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.gyromancy.rune_carving_table");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId,
                                            Inventory inventory,
                                            Player player) {
        return new RuneCarvingMenu(containerId, inventory, pos,
                ContainerLevelAccess.create(player.level(), pos));
    }
    @Override
    public void writeClientSideData(AbstractContainerMenu menu,
                                    RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
    }
}
