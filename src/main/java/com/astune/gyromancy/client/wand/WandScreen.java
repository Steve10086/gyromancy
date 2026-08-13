package com.astune.gyromancy.client.wand;

import com.astune.gyromancy.wand.WandMenu;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Wood-and-bronze wand configuration screen with a vertical center rail. */
public final class WandScreen extends AbstractContainerScreen<WandMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            "gyromancy", "textures/gui/sprites/wand_config_container.png");
    private static final int BACKGROUND_SOURCE_WIDTH = 256;
    private static final int BACKGROUND_SOURCE_HEIGHT = 256;
    private static final int WAND_CENTER_X = 88;
    private static final int WAND_CENTER_Y = 72;
    private static final int TEXT = 0xFFF4D7A2;
    private static final int MUTED_TEXT = 0xFFB88762;
    private static final int LINE = 0xFF9A654A;
    private static final int LINE_HIGHLIGHT = 0xFFC28A5C;
    private static final int MAGICAL = 0xFF6CC5B3;

    private float wandZ = 30f;

    public WandScreen(WandMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WandMenu.screenWidth(menu.layout());
        imageHeight = WandMenu.screenHeight(menu.layout());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight,
                BACKGROUND_SOURCE_WIDTH, BACKGROUND_SOURCE_HEIGHT);
        drawWandModel(graphics, leftPos + WAND_CENTER_X, topPos + WAND_CENTER_Y);
    }

    private void drawWandModel(GuiGraphics graphics, int centerX, int centerY) {
        ItemStack wand = menu.wandStack();
        if (wand.isEmpty()) return;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 30f);
        wandZ = (wandZ + 1f) % 360;

        pose.mulPose(Axis.ZP.rotationDegrees(45.0f));

        pose.translate(-centerX, -centerY, -30f);
        pose.mulPose(Axis.YP.rotationDegrees(wandZ));
        pose.translate(centerX, centerY, 30f);
        pose.scale(5f, 5f, 2f);
        graphics.renderItem(wand, -10, -9);
        pose.popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 80, 6, TEXT, false);


        for (int slot = 0; slot < menu.layout().slotCount(); slot++) {
            boolean left = (slot & 1) == 0;
            int slotX = WandMenu.canvasSlotX(menu.layout(), slot);
            int slotY = WandMenu.canvasSlotY(menu.layout(), slot, 0);
            Component plane = Component.translatable("screen.gyromancy.wand.plane", slot + 1);
            int labelX = left ? 8 : imageWidth - font.width(plane) - 8;
            graphics.drawString(font, plane, labelX, slotY + 5, TEXT, false);
            String offset = String.format(java.util.Locale.ROOT, "%.1f", menu.layout().slotOffset(slot));
            int offsetX = left ? 8 : imageWidth - font.width(offset) - 8;
            graphics.drawString(font, offset, offsetX, slotY + 15, MAGICAL, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
