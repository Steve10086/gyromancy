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
    private static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(
            "gyromancy", "wand_panel");
    private static final ResourceLocation SURFACE = ResourceLocation.fromNamespaceAndPath(
            "gyromancy", "wand_surface");
    private static final ResourceLocation HEADER = ResourceLocation.fromNamespaceAndPath(
            "gyromancy", "wand_header");
    private static final ResourceLocation SLOT_LEFT = ResourceLocation.fromNamespaceAndPath(
            "gyromancy", "wand_slot_left");
    private static final ResourceLocation SLOT_RIGHT = ResourceLocation.fromNamespaceAndPath(
            "gyromancy", "wand_slot_right");
    private static final ResourceLocation CENTER_RAIL = ResourceLocation.fromNamespaceAndPath(
            "gyromancy", "wand_center_rail");
    private static final ResourceLocation PIN = ResourceLocation.fromNamespaceAndPath(
            "gyromancy", "wand_pin");
    private static final int TEXT = 0xFFF4D7A2;
    private static final int MUTED_TEXT = 0xFFB88762;
    private static final int LINE = 0xFF9A654A;
    private static final int LINE_HIGHLIGHT = 0xFFC28A5C;
    private static final int MAGICAL = 0xFF6CC5B3;

    public WandScreen(WandMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WandMenu.screenWidth(menu.layout());
        imageHeight = WandMenu.screenHeight(menu.layout());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        int upperHeight = WandMenu.upperHeight(menu.layout());
        int inventoryTop = WandMenu.inventoryTop(menu.layout());

        graphics.blitSprite(SURFACE, x, y, imageWidth, imageHeight);
        graphics.blitSprite(PANEL, x, y, imageWidth, upperHeight);
        graphics.blitSprite(HEADER, x + 20, y + 8, imageWidth - 40, 26);

        graphics.fill(x + 10, y + inventoryTop - 10, x + imageWidth - 10,
                y + inventoryTop - 8, 0xFF1A111A);
        graphics.fill(x + 12, y + inventoryTop - 8, x + imageWidth - 12,
                y + inventoryTop - 7, 0xFFB7794A);
        graphics.fill(x + 12, y + inventoryTop - 6, x + imageWidth - 12,
                y + inventoryTop - 5, 0xFF35202A);
        drawInventorySlotBackdrops(graphics, x, y);

        int centerX = WandMenu.screenWidth(menu.layout()) / 2;
        int railX = centerX - WandMenu.CENTER_RAIL_WIDTH / 2;
        int railY = y + WandMenu.SLOT_START_Y - 2;
        int railHeight = upperHeight - WandMenu.SLOT_START_Y - 10;
        graphics.blitSprite(CENTER_RAIL, x + railX, railY,
                WandMenu.CENTER_RAIL_WIDTH, railHeight);

        drawSlotGuides(graphics, x, y);
        drawWandModel(graphics, x + centerX, y + 50, railHeight);
    }

    private void drawSlotGuides(GuiGraphics graphics, int originX, int originY) {
        for (int slot = 0; slot < menu.layout().slotCount(); slot++) {
            boolean left = (slot & 1) == 0;
            int slotX = WandMenu.canvasSlotX(menu.layout(), slot);
            for (int entry = 0; entry < menu.layout().slotCapacity(slot); entry++) {
                int slotY = WandMenu.canvasSlotY(menu.layout(), slot, entry);
                ResourceLocation texture = left ? SLOT_LEFT : SLOT_RIGHT;
                graphics.blitSprite(texture, originX + slotX - 1, originY + slotY - 1, 20, 20);

                int centerY = originY + slotY + 8;
                int railLeft = originX + WandMenu.screenWidth(menu.layout()) / 2
                        - WandMenu.CENTER_RAIL_WIDTH / 2;
                if (left) {
                    graphics.hLine(originX + slotX + 19, railLeft - 1, centerY, LINE);
                    graphics.hLine(originX + slotX + 19, railLeft - 1, centerY - 1, LINE_HIGHLIGHT);
                    graphics.blitSprite(PIN, originX + slotX + 20, centerY - 5, 12, 12);
                } else {
                    graphics.hLine(railLeft + WandMenu.CENTER_RAIL_WIDTH, originX + slotX - 2,
                            centerY, LINE);
                    graphics.hLine(railLeft + WandMenu.CENTER_RAIL_WIDTH, originX + slotX - 2,
                            centerY - 1, LINE_HIGHLIGHT);
                    graphics.blitSprite(PIN, originX + slotX - 12, centerY - 5, 12, 12);
                }
            }
        }
    }

    private void drawInventorySlotBackdrops(GuiGraphics graphics, int originX, int originY) {
        int inventoryX = WandMenu.inventoryLeft(menu.layout());
        int inventoryY = WandMenu.inventoryTop(menu.layout());
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawInventorySlotBackdrop(graphics, originX + inventoryX + column * 18 - 1,
                        originY + inventoryY + row * 18 - 1);
            }
        }
        for (int column = 0; column < 9; column++) {
            drawInventorySlotBackdrop(graphics, originX + inventoryX + column * 18 - 1,
                    originY + inventoryY + 58 - 1);
        }
    }

    private void drawInventorySlotBackdrop(GuiGraphics graphics, int x, int y) {
        graphics.blitSprite(SLOT_LEFT, x, y, 20, 20);
    }

    private void drawWandModel(GuiGraphics graphics, int centerX, int top, int railHeight) {
        ItemStack wand = menu.wandStack();
        if (wand.isEmpty()) return;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, top + railHeight / 2.0f, 30.0f);
        // The wand model's GUI transform already tilts it by -45 degrees. Add
        // 135 degrees here so the long body reads as a vertical centerpiece.
        pose.mulPose(Axis.ZP.rotationDegrees(225.0f));
        pose.scale(2.4f, 2.4f, 1.0f);
        graphics.renderItem(wand, -8, -8);
        pose.popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 28, 14, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.gyromancy.wand.modules"),
                28, 38, MUTED_TEXT, false);

        for (int slot = 0; slot < menu.layout().slotCount(); slot++) {
            boolean left = (slot & 1) == 0;
            int slotX = WandMenu.canvasSlotX(menu.layout(), slot);
            int slotY = WandMenu.canvasSlotY(menu.layout(), slot, 0);
            int labelX = left ? slotX - 28 : slotX + 24;
            graphics.drawString(font, Component.translatable("screen.gyromancy.wand.plane",
                    slot + 1), labelX, slotY + 5, TEXT, false);
            String offset = String.format(java.util.Locale.ROOT, "%.1f", menu.layout().slotOffset(slot));
            int offsetX = left ? slotX - 28 : slotX + 24;
            graphics.drawString(font, offset, offsetX, slotY + 15, MAGICAL, false);
        }

        int inventoryTop = WandMenu.inventoryTop(menu.layout());
        graphics.drawString(font, playerInventoryTitle, WandMenu.inventoryLeft(menu.layout()),
                inventoryTop - 2, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.gyromancy.wand.inventory_hint"),
                WandMenu.inventoryLeft(menu.layout()) + 74, inventoryTop - 2, MUTED_TEXT, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
