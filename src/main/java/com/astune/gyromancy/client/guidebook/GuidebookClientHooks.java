package com.astune.gyromancy.client.guidebook;

import com.astune.gyromancy.guidebook.GuidebookCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Client-only entry point kept separate from the common item class. */
@OnlyIn(Dist.CLIENT)
public final class GuidebookClientHooks {

    private GuidebookClientHooks() {}

    public static void open(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack localizedStack = stack.copy();
        localizedStack.set(DataComponents.WRITTEN_BOOK_CONTENT,
                GuidebookCatalog.createWrittenBookContent(
                        minecraft.getLanguageManager().getSelected()));
        BookViewScreen.BookAccess access = BookViewScreen.BookAccess.fromItem(localizedStack);
        if (access != null) {
            minecraft.setScreen(new BookViewScreen(access));
        }
    }
}
