package com.astune.gyromancy;

import javax.annotation.Nullable;

import com.astune.gyromancy.client.ElementDebugRenderer;
import com.astune.gyromancy.client.PaintCameraController;
import com.astune.gyromancy.client.array.ArrayClientState;
import com.astune.gyromancy.client.canvas.CanvasClientState;
import com.astune.gyromancy.client.canvas.CanvasEditorKeyMappings;
import com.astune.gyromancy.client.effect.ClientRayEffects;
import com.astune.gyromancy.client.effect.FlipbookEffect;
import com.astune.gyromancy.client.effect.VortexOrbitEffect;
import com.astune.gyromancy.client.effect.WandProjectionGlowRenderer;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Gyromancy.MODID, dist = Dist.CLIENT)
public class GyromancyClient {

    @Nullable
    private static VortexOrbitEffect testVortex;

    public GyromancyClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(PaintCameraController::registerKeyMappings);
        modEventBus.addListener(CanvasEditorKeyMappings::register);

        // ── Client commands — /gyromancy debug ──
        NeoForge.EVENT_BUS.<RegisterClientCommandsEvent>addListener(event -> {
            var node = Commands.literal("gyromancy")
                    .then(Commands.literal("debug")
                            .then(Commands.argument("state", BoolArgumentType.bool())
                                    .executes(ctx -> {
                                        boolean state = BoolArgumentType.getBool(ctx, "state");
                                        ElementDebugRenderer.setEnabled(state);
                                        ctx.getSource().sendSuccess(
                                                () -> Component.literal("Element debug overlay: "
                                                        + (state ? "ON" : "OFF")),
                                                false
                                        );
                                        return 1;
                                    })
                            )
                    )
                    .then(Commands.literal("vortex")
                            .then(Commands.argument("effect", StringArgumentType.word())
                                .executes(ctx -> {
                                    String effect = StringArgumentType.getString(ctx, "effect");
                                    if (testVortex != null) {
                                        testVortex.kill();
                                        testVortex = null;
                                        if (effect.equals("Disable")){
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Vortex effect: OFF"), false);
                                            return 1;
                                        }
                                    }
                                    if(!effect.isEmpty()){
                                        var player = Minecraft.getInstance().player;
                                        if (player == null) return 0;
                                        testVortex = new VortexOrbitEffect(
                                                ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, effect),
                                                player.level(), player::position,
                                                30, 3f, 4f, 60,
                                                new net.minecraft.world.phys.Vec3(0, 1, 0), 2f, 20);
                                        testVortex.start();
                                        ctx.getSource().sendSuccess(
                                                () -> Component.literal("Vortex effect: ON (30 particles)"), false);
                                        return 1;
                                    }
                                    return 1;
                                })
                            )
                    );
            event.getDispatcher().register(node);
            Gyromancy.LOGGER.info("[Gyromancy] Client /gyromancy debug registered");
        });

        // ── Debug renderer — AFTER_PARTICLES overlay ──
        NeoForge.EVENT_BUS.<RenderLevelStageEvent>addListener(
                e -> ElementDebugRenderer.onRenderLevelStage(e));

        NeoForge.EVENT_BUS.<RenderLevelStageEvent>addListener(
                ClientRayEffects::onRenderLevelStage);

        NeoForge.EVENT_BUS.<RenderLevelStageEvent>addListener(
                FlipbookEffect::onRenderLevelStage);

        NeoForge.EVENT_BUS.<RenderLevelStageEvent>addListener(
                WandProjectionGlowRenderer::onRenderLevelStage);

        // ── Vortex effect tick (game-time guard inside) ──
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,
                (RenderFrameEvent.Pre e) -> PaintCameraController.onRenderFramePre(e));
        NeoForge.EVENT_BUS.<ClientTickEvent.Post>addListener(
                PaintCameraController::onClientTick);
        NeoForge.EVENT_BUS.addListener(CanvasClientState::onEntityLeave);
        NeoForge.EVENT_BUS.addListener(CanvasClientState::onLogout);
        NeoForge.EVENT_BUS.addListener(ArrayClientState::onLogout);
        NeoForge.EVENT_BUS.<ViewportEvent.ComputeCameraAngles>addListener(
                PaintCameraController::onComputeCameraAngles);
        NeoForge.EVENT_BUS.<ViewportEvent.ComputeFov>addListener(
                PaintCameraController::onComputeFov);

        NeoForge.EVENT_BUS.<RenderLevelStageEvent>addListener(e -> {
            if (testVortex != null) testVortex.tick();
        });

        Gyromancy.LOGGER.info("[Gyromancy] Client handlers wired on NeoForge.EVENT_BUS");
    }
}
