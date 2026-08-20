package com.astune.gyromancy.client.guidebook;

import com.astune.gyromancy.guidebook.GuidebookCatalog;
import com.astune.gyromancy.guidebook.GuidebookContent.Image;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/** Draws the structured page images in the unused margin of vanilla's book texture. */
@OnlyIn(Dist.CLIENT)
public final class GuidebookBookImageRenderer {

    private static final int MAX_WIDTH = 34;
    private static final int MAX_HEIGHT = 34;
    private static final int IMAGE_X_OFFSET = 153;
    private static final int IMAGE_Y = 32;

    private GuidebookBookImageRenderer() {}

    public static void render(GuiGraphics graphics, String language, int pageIndex, int screenWidth) {
        List<Image> images = GuidebookCatalog.imagesOnNativePage(language, pageIndex);
        if (images.isEmpty()) {
            return;
        }

        int bookLeft = (screenWidth - 192) / 2;
        int y = IMAGE_Y;
        for (Image image : images) {
            double scale = Math.min(
                    (double) MAX_WIDTH / Math.max(1, image.width()),
                    (double) MAX_HEIGHT / Math.max(1, image.height()));
            int width = Math.max(1, (int) Math.round(image.width() * scale));
            int height = Math.max(1, (int) Math.round(image.height() * scale));
            if (y + height > 151) {
                break;
            }
            graphics.blit(image.texture(), bookLeft + IMAGE_X_OFFSET, y, width, height,
                    0, 0, image.sourceWidth(), image.sourceHeight(),
                    image.sourceWidth(), image.sourceHeight());
            y += height + 4;
        }
    }
}
