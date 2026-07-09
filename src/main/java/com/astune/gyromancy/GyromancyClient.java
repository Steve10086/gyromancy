package com.astune.gyromancy;

import javax.annotation.Nullable;

import com.astune.gyromancy.client.ElementDebugRenderer;
import com.astune.gyromancy.client.effect.ClientRayEffects;
import com.astune.gyromancy.client.effect.FlipbookEffect;
import com.astune.gyromancy.client.effect.VortexOrbitEffect;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Gyromancy.MODID, dist = Dist.CLIENT)
public class GyromancyClient {

    @Nullable
    private static VortexOrbitEffect testVortex;

    public GyromancyClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

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
                            .executes(ctx -> {
                                if (testVortex != null) {
                                    testVortex.kill();
                                    testVortex = null;
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Vortex effect: OFF"), false);
                                    return 1;
                                }
                                var player = Minecraft.getInstance().player;
                                if (player == null) return 0;
                                testVortex = new VortexOrbitEffect(
                                        player.level(), player::position,
                                        30, 4f, 60,
                                        new net.minecraft.world.phys.Vec3(0, 1, 0), 2f);
                                testVortex.emit();
                                ctx.getSource().sendSuccess(
                                        () -> Component.literal("Vortex effect: ON (30 particles)"), false);
                                return 1;
                            })
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

        Gyromancy.LOGGER.info("[Gyromancy] Client handlers wired on NeoForge.EVENT_BUS");
    }
}
