package com.astune.gyromancy.client;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.client.entity.FireballRenderer;
import com.astune.gyromancy.client.entity.ManaballRenderer;
import com.astune.gyromancy.client.entity.OldFireballRenderer;
import com.astune.gyromancy.client.entity.ElementBallRenderer;
import com.astune.gyromancy.client.entity.WaterBallRenderer;
import com.astune.gyromancy.client.entity.WindFieldRenderer;
import com.astune.gyromancy.client.canvas.CanvasEntityRenderer;
import com.astune.gyromancy.client.canvas.CanvasTooltipComponent;
import com.astune.gyromancy.client.item.WandClientItemExtensions;
import com.astune.gyromancy.client.glyph.GlyphImageProvider;
import com.astune.gyromancy.client.glyph.GlyphRenderer;
import com.astune.gyromancy.client.guide.AutoLayoutPageComponent;
import com.astune.gyromancy.client.guide.GuideLinkFormatter;
import com.astune.gyromancy.client.guide.MarkdownPageComponent;
import com.astune.gyromancy.item.CompassItem;
import com.astune.gyromancy.canvas.CanvasTooltipImage;
import com.astune.gyromancy.registry.ModEntities;
import com.astune.gyromancy.registry.ModItems;
import com.astune.gyromancy.registry.ModMenus;
import com.astune.gyromancy.client.wand.WandScreen;
import com.astune.gyromancy.client.canvas.RuneCarvingScreen;
import com.astune.painter.api.imageProvider.CanvasImageProviderRegistry;
import com.astune.painter.api.render.CanvasRendererRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.favouriteless.modopedia.api.registries.client.PageComponentRegistry;
import net.favouriteless.modopedia.api.registries.client.TextFormatterRegistry;

@EventBusSubscriber(modid = Gyromancy.MODID, value = Dist.CLIENT)
public final class ClientSetup {

    private static final ResourceLocation TORNADO_FX =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "wind_tornado");

    private ClientSetup() {}

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Text formatters are consumed while Modopedia parses native JSON
        // pages. Register the guide link formatter before the deferred work
        // below so static menu pages cannot be initialized without it.
        TextFormatterRegistry.get().register(new GuideLinkFormatter());
        event.enqueueWork(() -> {
            CanvasImageProviderRegistry.register(GlyphImageProvider.INSTANCE, 2);
            CanvasRendererRegistry.registerPixelRenderer(GlyphRenderer.INSTANCE, 2);
            PageComponentRegistry.get().register(
                    ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "auto_layout"),
                    AutoLayoutPageComponent::new);
            PageComponentRegistry.get().register(
                    ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "markdown"),
                    MarkdownPageComponent::new);
            ItemProperties.register(
                    ModItems.COMPASS.get(),
                    ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "stored_pen"),
                    (stack, level, entity, seed) -> CompassItem.hasStoredPen(stack) ? 1.0F : 0.0F);
            Gyromancy.LOGGER.info("[Gyromancy] Glyph render pipeline registered");
        });
    }

    @SubscribeEvent
    static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(WandClientItemExtensions.INSTANCE, ModItems.WAND.get());
    }

    @SubscribeEvent
    static void registerClientTooltipComponents(
            RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(CanvasTooltipImage.class, CanvasTooltipComponent::new);
    }

    @SubscribeEvent
    static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.FIREBALL.get(), FireballRenderer::new);
        event.registerEntityRenderer(ModEntities.ILLUMINATION.get(),
                context -> new ElementBallRenderer<>(context, "luminate", 0xFFFFFFFF));
        event.registerEntityRenderer(ModEntities.OLD_FIREBALL.get(), OldFireballRenderer::new);
        event.registerEntityRenderer(ModEntities.MANABALL.get(), ManaballRenderer::new);
        event.registerEntityRenderer(ModEntities.WATER_BALL.get(), WaterBallRenderer::new);
        event.registerEntityRenderer(ModEntities.ICE_BALL.get(), context -> new ElementBallRenderer<>(context, "frozen_core", 0xFFD8F4FF));
        event.registerEntityRenderer(ModEntities.DRY_BALL.get(), context -> new ElementBallRenderer<>(context, "mana_ball", 0xFFD8B36A));
        event.registerEntityRenderer(ModEntities.TORNADO_BALL.get(), context ->
                new ElementBallRenderer<>(context, TORNADO_FX, 0xFFB8F8FF));
        event.registerEntityRenderer(ModEntities.WIND_FIELD.get(), WindFieldRenderer::new);
        event.registerEntityRenderer(ModEntities.CANVAS.get(), CanvasEntityRenderer::new);
        event.registerEntityRenderer(ModEntities.CANVAS_PROJECTION.get(), CanvasEntityRenderer::new);
        event.registerEntityRenderer(ModEntities.WAND_PROJECTION.get(), CanvasEntityRenderer::new);
    }

    @SubscribeEvent
    static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(CanvasEntityRenderer.collapsedModel());
    }

    @SubscribeEvent
    static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.WAND.get(), WandScreen::new);
        event.register(ModMenus.RUNE_CARVING.get(), RuneCarvingScreen::new);
    }
}
