package com.astune.gyromancy.client.canvas;

import com.astune.gyromancy.canvas.CanvasTooltipImage;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

/** Renders a portable canvas document as a pixel-art tooltip image. */
public final class CanvasTooltipComponent implements ClientTooltipComponent {
    private static final int IMAGE_SIZE = 64;

    private final CanvasDynamicTexture texture;

    public CanvasTooltipComponent(CanvasTooltipImage image) {
        this.texture = CanvasTooltipTextureCache.texture(image.document());
    }

    @Override
    public int getHeight() {
        return IMAGE_SIZE;
    }

    @Override
    public int getWidth(Font font) {
        return IMAGE_SIZE;
    }

    @Override
    public void renderImage(Font font,
                            int x,
                            int y,
                            GuiGraphics graphics) {
        graphics.blit(
                texture.location(),
                x,
                y,
                IMAGE_SIZE,
                IMAGE_SIZE,
                0.0F,
                0.0F,
                texture.width(),
                texture.height(),
                texture.width(),
                texture.height());
    }
}
