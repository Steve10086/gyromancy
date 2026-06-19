package com.astune.gyromancy.client;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementConcentrations;
import com.astune.gyromancy.api.element.ElementType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Debug overlay for element concentrations. Renders flat translucent quads.
 * Reads from a client-side mirror map populated by {@code SyncDebugElementPacket}.
 */
public final class ElementDebugRenderer {

    private ElementDebugRenderer() {}

    private static volatile boolean enabled = false;

    private static final int RADIUS = 12;
    private static final float OVERLAY_Y_OFFSET = 0.03f;
    private static final long ALPHA_REFERENCE = 6000L;
    private static final float MAX_ALPHA = 0.5f;

    // ── Client-side mirror of overrides (populated by network packet) ──
    private static final Map<BlockPos, ElementConcentrations> debugData = new ConcurrentHashMap<>();

    private static final int[] ELEMENT_COLORS = {
            0xFF_7EC8E3, 0xFF_FF6B35, 0xFF_4CAF50, 0xFF_8D6E63,
            0xFF_FFF176, 0xFF_7B1FA2, 0xFF_42A5F5, 0xFF_EC407A,
            0xFF_26C6DA
    };

    public static void setEnabled(boolean e) { enabled = e; }
    public static boolean isEnabled() { return enabled; }

    /** Called by the client packet handler to store a debug data point */
    public static void putDebugData(BlockPos pos, ElementConcentrations conc) {
        debugData.put(pos, conc);
    }

    /** Atomically replace the entire debug dataset (used by batch sync). */
    public static void replaceDebugData(Map<BlockPos, ElementConcentrations> newData) {
        debugData.clear();
        debugData.putAll(newData);
    }

    /** Returns an unmodifiable view of debug data */
    public static Map<BlockPos, ElementConcentrations> getDebugData() {
        return Collections.unmodifiableMap(debugData);
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!enabled) return;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || debugData.isEmpty()) return;

        BlockPos center = mc.player.blockPosition();
        int minY = Math.max(mc.level.getMinBuildHeight(), center.getY() - RADIUS);
        int maxY = Math.min(mc.level.getMaxBuildHeight(), center.getY() + RADIUS);

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer quadConsumer = bufferSource.getBuffer(RenderType.debugQuads());

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        int drawn = 0;

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        for (BlockPos pos : BlockPos.betweenClosed(
                center.getX() - RADIUS, minY, center.getZ() - RADIUS,
                center.getX() + RADIUS, maxY, center.getZ() + RADIUS)) {
            ElementConcentrations conc = debugData.get(pos);
            if (conc == null) continue;

            int dominantIdx = findDominantElement(conc);
            long dominantValue = conc.values()[dominantIdx];
            float alpha = Math.clamp((float) dominantValue / (float) ALPHA_REFERENCE, 0f, 1f) * MAX_ALPHA;
            if (alpha < 0.02f) continue;

            int color = ELEMENT_COLORS[dominantIdx];
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            float x = pos.getX(), y = pos.getY() + OVERLAY_Y_OFFSET, z = pos.getZ();

            quadConsumer.addVertex(poseStack.last(), x, y, z).setColor(r, g, b, alpha);
            quadConsumer.addVertex(poseStack.last(), x, y, z + 1).setColor(r, g, b, alpha);
            quadConsumer.addVertex(poseStack.last(), x + 1, y, z + 1).setColor(r, g, b, alpha);
            quadConsumer.addVertex(poseStack.last(), x + 1, y, z).setColor(r, g, b, alpha);
            drawn++;
        }

        poseStack.popPose();
        bufferSource.endBatch();

        if (drawn > 0) {
            // Silent — working
        }
    }

    private static int findDominantElement(ElementConcentrations conc) {
        int dominant = 0;
        long maxVal = 0;
        for (int i = 0; i < ElementType.COUNT; i++) {
            if (conc.values()[i] > maxVal) {
                maxVal = conc.values()[i];
                dominant = i;
            }
        }
        return dominant;
    }
}
