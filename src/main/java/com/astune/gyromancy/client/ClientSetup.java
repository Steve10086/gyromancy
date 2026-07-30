package com.astune.gyromancy.client;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.client.entity.FireballRenderer;
import com.astune.gyromancy.client.entity.ManaballRenderer;
import com.astune.gyromancy.client.entity.OldFireballRenderer;
import com.astune.gyromancy.client.entity.ElementBallRenderer;
import com.astune.gyromancy.client.entity.WaterBallRenderer;
import com.astune.gyromancy.client.canvas.CanvasEntityRenderer;
import com.astune.gyromancy.client.glyph.GlyphImageProvider;
import com.astune.gyromancy.client.glyph.GlyphRenderer;
import com.astune.gyromancy.registry.ModEntities;
import com.astune.painter.api.imageProvider.CanvasImageProviderRegistry;
import com.astune.painter.api.render.CanvasRendererRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = Gyromancy.MODID, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {}

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            CanvasImageProviderRegistry.register(GlyphImageProvider.INSTANCE, 2);
            CanvasRendererRegistry.registerPixelRenderer(GlyphRenderer.INSTANCE, 2);
            Gyromancy.LOGGER.info("[Gyromancy] Glyph render pipeline registered");
        });
    }

    @SubscribeEvent
    static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.FIREBALL.get(), FireballRenderer::new);
        event.registerEntityRenderer(ModEntities.OLD_FIREBALL.get(), OldFireballRenderer::new);
        event.registerEntityRenderer(ModEntities.MANABALL.get(), ManaballRenderer::new);
        event.registerEntityRenderer(ModEntities.WATER_BALL.get(), WaterBallRenderer::new);
        event.registerEntityRenderer(ModEntities.ICE_BALL.get(), context -> new ElementBallRenderer<>(context, "frozen_core", 0xFFD8F4FF));
        event.registerEntityRenderer(ModEntities.DRY_BALL.get(), context -> new ElementBallRenderer<>(context, "mana_ball", 0xFFD8B36A));
        event.registerEntityRenderer(ModEntities.CANVAS.get(), CanvasEntityRenderer::new);
    }
}
