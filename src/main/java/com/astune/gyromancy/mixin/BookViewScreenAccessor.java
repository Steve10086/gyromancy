package com.astune.gyromancy.mixin;

import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Narrow access to the native book state needed by the image overlay. */
@Mixin(BookViewScreen.class)
public interface BookViewScreenAccessor {

    @Accessor("bookAccess")
    BookViewScreen.BookAccess gyromancy$getBookAccess();

    @Accessor("currentPage")
    int gyromancy$getCurrentPage();
}
