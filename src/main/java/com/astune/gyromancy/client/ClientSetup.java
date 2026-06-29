package com.astune.gyromancy.client;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.client.glyph.GlyphImageProvider;
import com.astune.gyromancy.client.glyph.GlyphRenderer;
import com.astune.painter.api.imageProvider.CanvasImageProviderRegistry;
import com.astune.painter.api.render.CanvasRendererRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-side registration for Gyromancy's rendering pipeline.
 *
 * <p>Registers a single generic {@link GlyphImageProvider} and
 * {@link GlyphRenderer} — color is determined per-pixel by the
 * symbol_id value in the canvas effect layer.
 */
@EventBusSubscriber(modid = Gyromancy.MODID, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {}

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Register image provider — generates colored glyph textures
            CanvasImageProviderRegistry.register(GlyphImageProvider.INSTANCE, 2);
            // Register renderer — draws glyph overlay at full brightness
            CanvasRendererRegistry.registerPixelRenderer(GlyphRenderer.INSTANCE, 2);

            Gyromancy.LOGGER.info("[Gyromancy] Glyph render pipeline registered");
        });
    }
}
