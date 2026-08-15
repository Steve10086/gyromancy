package com.astune.gyromancy.client.wand;

import com.astune.gyromancy.wand.WandMenu;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Wood-and-bronze wand configuration screen with a vertical center rail. */
public final class WandScreen extends AbstractContainerScreen<WandMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            "gyromancy", "textures/gui/sprites/wand_config_container.png");
    private static final ResourceLocation SLOT_LEFT = ResourceLocation.fromNamespaceAndPath(
            "gyromancy", "wand_slot_left");
    private static final ResourceLocation SLOT_RIGHT = ResourceLocation.fromNamespaceAndPath(
            "gyromancy", "wand_slot_right");
    private static final int BACKGROUND_SOURCE_WIDTH = 256;
    private static final int BACKGROUND_SOURCE_HEIGHT = 256;
    private static final float WAND_CENTER_X = 81.5f;
    private static final int WAND_CENTER_Y = 72;
    private static final float WAND_STATIC_ROTATION = 90.0f;
    private static final float WAND_MODEL_SCALE = 70.0f;
    private static final float WAND_MODEL_SCALE_Y = -70.0f;

    // The Blockbench model's geometry is centered at approximately
    // (-0.575..16, 8.25..11.975, 6.5..10) model pixels. These are the
    // corresponding offsets from the item's built-in 8,8,8 render origin.
    private static final float WAND_MODEL_PIVOT_X = -0.01796875f;
    private static final float WAND_MODEL_PIVOT_Y = 0.13203125f;
    private static final float WAND_MODEL_PIVOT_Z = 0.015625f;
    private static final int TEXT = 0xFFF4D7A2;
    private static final int MUTED_TEXT = 0xFFB88762;
    private static final int LINE = 0xFF9A654A;
    private static final int LINE_HIGHLIGHT = 0xFFC28A5C;
    private static final int MAGICAL = 0xFF6CC5B3;

    private float wandRotation = 0.0f;

    public WandScreen(WandMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WandMenu.screenWidth(menu.layout());
        imageHeight = WandMenu.screenHeight(menu.layout());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // AbstractContainerScreen updates hoveredSlot while rendering, but
        // NeoForge's replicated render method leaves tooltip dispatch to each
        // concrete container screen.
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight,
                BACKGROUND_SOURCE_WIDTH, BACKGROUND_SOURCE_HEIGHT);
        renderWandSlotBackgrounds(graphics);
        drawWandModel(graphics, leftPos + WAND_CENTER_X, topPos + WAND_CENTER_Y);
    }

    private void renderWandSlotBackgrounds(GuiGraphics graphics) {
        for (int slot = 0; slot < menu.layout().slotCount(); slot++) {
            for (int entry = 0; entry < menu.layout().slotCapacity(slot); entry++) {
                int slotX = WandMenu.canvasSlotX(menu.layout(), slot, entry);
                boolean left = slotX < imageWidth / 2;
                ResourceLocation sprite = left ? SLOT_LEFT : SLOT_RIGHT;
                int x = leftPos + slotX - 1;
                int y = topPos + WandMenu.canvasSlotY(menu.layout(), slot, entry) - 1;
                graphics.blitSprite(sprite, x, y, WandMenu.SLOT_SIZE + 1, WandMenu.SLOT_SIZE + 1);
            }
        }
    }

    private void drawWandModel(GuiGraphics graphics, float centerX, int centerY) {
        ItemStack wand = menu.wandStack();
        if (wand.isEmpty()) return;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, -20);
        wandRotation = (wandRotation + 1f) % 360;

        // Use a context without the model's display.gui transform. The wand
        // screen supplies its own orientation and pivot below.
        BakedModel model = minecraft.getItemRenderer().getModel(
                wand, minecraft.level, minecraft.player, 0);
        pose.translate(5.5f, 0f, 150.0f);
        pose.mulPose(Axis.YP.rotationDegrees(wandRotation));
        pose.mulPose(Axis.ZP.rotationDegrees(WAND_STATIC_ROTATION));
        pose.scale(WAND_MODEL_SCALE, WAND_MODEL_SCALE_Y, WAND_MODEL_SCALE);
        pose.translate(-WAND_MODEL_PIVOT_X, -WAND_MODEL_PIVOT_Y, -WAND_MODEL_PIVOT_Z);
        boolean flatItem = !model.usesBlockLight();
        if (flatItem) {
            Lighting.setupForFlatItems();
        }
        minecraft.getItemRenderer().render(
                wand,
                ItemDisplayContext.NONE,
                false,
                pose,
                graphics.bufferSource(),
                15728880,
                OverlayTexture.NO_OVERLAY,
                model);
        graphics.flush();
        if (flatItem) {
            Lighting.setupFor3DItems();
        }
        pose.popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 80, 6, TEXT, false);


        for (int slot = 0; slot < menu.layout().slotCount(); slot++) {
            int slotX = WandMenu.canvasSlotX(menu.layout(), slot, 0);
            boolean left = slotX < imageWidth / 2;
            int slotY = WandMenu.canvasSlotY(menu.layout(), slot, 0);
            Component plane = Component.translatable("screen.gyromancy.wand.plane", slot + 1);
            int labelX = left ? 8 : imageWidth - font.width(plane) - 8;
            graphics.drawString(font, plane, labelX, slotY - 10, TEXT, false);
            String offset = String.format(java.util.Locale.ROOT, "%.1f", menu.layout().slotOffset(slot));
            int offsetX = left ? 8 : imageWidth - font.width(offset) - 8;
            graphics.drawString(font, offset, offsetX, slotY, MAGICAL, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
