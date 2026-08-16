package com.astune.gyromancy.client.compat.jei;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.client.canvas.CanvasEditorScreen;
import com.astune.gyromancy.client.canvas.RuneCarvingScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Optional JEI integration; this class is only loaded by JEI when JEI is present. */
@JeiPlugin
public final class GyromancyJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(CanvasEditorScreen.class,
                new IGuiContainerHandler<CanvasEditorScreen>() {
                    @Override
                    public List<Rect2i> getGuiExtraAreas(CanvasEditorScreen screen) {
                        return List.of(screen.jeiToolbarArea());
                    }
                });
        registration.addGuiContainerHandler(RuneCarvingScreen.class,
                new IGuiContainerHandler<RuneCarvingScreen>() {
                    @Override
                    public List<Rect2i> getGuiExtraAreas(RuneCarvingScreen screen) {
                        return List.of(screen.jeiToolbarArea());
                    }
                });
    }
}
