package com.astune.gyromancy.mixin;

import com.astune.gyromancy.client.guidebook.GuidebookBookImageRenderer;
import com.astune.gyromancy.guidebook.GuidebookCatalog;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds catalog images while leaving vanilla book layout, input and link handling intact. */
@Mixin(BookViewScreen.class)
public abstract class BookViewScreenMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void gyromancy$renderGuidebookImages(GuiGraphics graphics, int mouseX, int mouseY,
                                                  float partialTick, CallbackInfo callbackInfo) {
        BookViewScreenAccessor accessor = (BookViewScreenAccessor) this;
        int page = accessor.gyromancy$getCurrentPage();
        if (page < 0) {
            return;
        }

        BookViewScreen.BookAccess access = accessor.gyromancy$getBookAccess();
        if (access == null || access.getPageCount() == 0
                || !(access.getPage(0) instanceof Component firstPage)) {
            return;
        }
        String language = GuidebookCatalog.guidebookLanguage(firstPage);
        if (language == null || page >= GuidebookCatalog.nativePageCount(language)) {
            return;
        }

        GuidebookBookImageRenderer.render(graphics, language, page,
                Minecraft.getInstance().getWindow().getGuiScaledWidth());
    }
}
