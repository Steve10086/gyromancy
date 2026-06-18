package com.astune.gyromancy.client;

import com.astune.gyromancy.api.element.ElementConcentrations;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.element.ElementStorageManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Debug overlay renderer for element concentrations.
 *
 * <p>Renders a flat translucent quad on top of each overridden block,
 * colored by the dominant element and alpha-scaled by its concentration value.
 * Also renders floating text showing the element abbreviation and value.</p>
 *
 * <p>Toggle with {@code /gyromancy debug true|false}.</p>
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = "gyromancy")
public final class ElementDebugRenderer {

    private ElementDebugRenderer() {}

    private static volatile boolean enabled = false;

    /** Render radius in blocks */
    private static final int RADIUS = 12;

    /** Height offset above block surface for the flat overlay quad */
    private static final float OVERLAY_Y_OFFSET = 0.03f;

    /** Multiplier for alpha = concentration * ALPHA_SCALE */
    private static final float ALPHA_SCALE = 0.5f;

    // ── Element colors (ARGB) ──
    private static final int[] ELEMENT_COLORS = {
            0xFF_7EC8E3,  // WIND  — 天蓝
            0xFF_FF6B35,  // FIRE  — 橙红
            0xFF_4CAF50,  // WOOD  — 绿色
            0xFF_8D6E63,  // EARTH — 棕色
            0xFF_FFF176,  // LIGHT — 金黄
            0xFF_7B1FA2,  // DARK  — 紫色
            0xFF_42A5F5,  // SPACE — 蓝色
            0xFF_EC407A,  // TIME  — 品红
            0xFF_26C6DA,  // MANA  — 青色
    };

    public static void setEnabled(boolean enabled) {
        ElementDebugRenderer.enabled = enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    @SubscribeEvent
    static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!enabled) return;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || mc.player == null) return;

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        BlockPos center = BlockPos.containing(camPos);
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - RADIUS);
        int maxY = Math.min(level.getMaxBuildHeight(), center.getY() + RADIUS);

        // ── Phase 1: Flat quad overlay ──
        VertexConsumer quadConsumer = bufferSource.getBuffer(RenderType.debugQuads());

        for (BlockPos pos : BlockPos.betweenClosed(
                center.getX() - RADIUS, minY, center.getZ() - RADIUS,
                center.getX() + RADIUS, maxY, center.getZ() + RADIUS)) {
            if (!level.isLoaded(pos)) continue;
            if (!ElementStorageManager.INSTANCE.isOverridden(level, pos)) continue;

            ElementConcentrations conc = ElementStorageManager.INSTANCE.get(level, pos);
            int dominantIdx = findDominantElement(conc);
            float alpha = Math.clamp(conc.values()[dominantIdx] * ALPHA_SCALE, 0.05f, 0.9f);

            int color = ELEMENT_COLORS[dominantIdx];
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;

            float x = pos.getX();
            float y = pos.getY() + OVERLAY_Y_OFFSET;
            float z = pos.getZ();

            quadConsumer.addVertex(x,     y, z).setColor(r, g, b, alpha);
            quadConsumer.addVertex(x,     y, z + 1).setColor(r, g, b, alpha);
            quadConsumer.addVertex(x + 1, y, z + 1).setColor(r, g, b, alpha);
            quadConsumer.addVertex(x + 1, y, z).setColor(r, g, b, alpha);
        }

        // ── Phase 2: End batch and optional text rendering ──
        bufferSource.endBatch();
    }

    /**
     * Finds the index of the element with the highest concentration at this position.
     */
    private static int findDominantElement(ElementConcentrations conc) {
        int dominant = 0;
        float maxVal = 0f;
        for (int i = 0; i < ElementType.COUNT; i++) {
            if (conc.values()[i] > maxVal) {
                maxVal = conc.values()[i];
                dominant = i;
            }
        }
        return dominant;
    }
}
