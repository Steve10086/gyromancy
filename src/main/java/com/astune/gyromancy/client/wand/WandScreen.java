package com.astune.gyromancy.client.wand;

import com.astune.gyromancy.wand.WandMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Compact two-plane canvas storage screen for the wand. */
public final class WandScreen extends AbstractContainerScreen<WandMenu> {
    public WandScreen(WandMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 176;
        imageWidth = Math.max(imageWidth, 16 + menu.layout().slotCount() * 72);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF20202A);
        graphics.fill(x + 8, y + 8, x + imageWidth - 8, y + 72, 0xFF30303B);
        graphics.fill(x + 8, y + 78, x + imageWidth - 8, y + 168, 0xFF18181E);
        for (int slot = 0; slot < menu.layout().slotCount(); slot++) {
            int slotX = 43 + slot * 72;
            graphics.fill(x + slotX, y + 18, x + slotX + 36, y + 64, 0xFF24242D);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 6, 0xFFFFFF, false);
        for (int slot = 0; slot < menu.layout().slotCount(); slot++) {
            graphics.drawString(font,
                    Component.literal(String.format("%.2f", menu.layout().slotOffset(slot))),
                    44 + slot * 72, 10, 0xB9D7FF, false);
        }
        graphics.drawString(font, playerInventoryTitle, 8, 76, 0xFFFFFF, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
