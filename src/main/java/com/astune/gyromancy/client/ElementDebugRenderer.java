package com.astune.gyromancy.client;

import com.astune.gyromancy.api.element.ElementConcentrations;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.element.ElementBiomeProvider;
import com.astune.gyromancy.network.SyncGlyphPacket.GlyphData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

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
    private static final double LABEL_FACE_OFFSET = 0.03;
    private static final double ELEMENT_LABEL_Y_OFFSET = 1.15;
    private static final float ELEMENT_LABEL_SCALE = 0.014f;
    private static final int ELEMENT_ABOVE_COLOR = 0xFFFFFFFF;
    private static final int ELEMENT_BELOW_COLOR = 0xFFFF4040;
    private static final double GLYPH_ARROW_MIN = 0.25;
    private static final double GLYPH_ARROW_MAX = 1.0;

    // ── Client-side mirror of overrides (populated by network packet) ──
    private static volatile Map<BlockPos, ElementConcentrations> debugData = Map.of();
    private static final Map<Integer, GlyphData> glyphData = new ConcurrentHashMap<>();

    private static final int[] ELEMENT_COLORS = {
            0xFF_7EC8E3, 0xFF_FF6B35, 0xFF_4F9DFF, 0xFF_8D6E63,
            0xFF_FFF176, 0xFF_7B1FA2, 0xFF_42A5F5, 0xFF_EC407A,
            0xFF_26C6DA
    };

    public static void setEnabled(boolean e) { enabled = e; }
    public static boolean isEnabled() { return enabled; }

    /** Called by the client packet handler to store a debug data point */
    public static void putDebugData(BlockPos pos, ElementConcentrations conc) {
        var m = new java.util.HashMap<>(debugData);
        m.put(pos, conc);
        debugData = java.util.Collections.unmodifiableMap(m);
    }

    /** Atomically replace the entire debug dataset (used by batch sync). */
    public static void replaceDebugData(Map<BlockPos, ElementConcentrations> newData) {
        debugData = Map.copyOf(newData);
    }

    /** Atomically replace the glyph debug dataset. */
    public static void replaceGlyphData(java.util.Collection<GlyphData> newData) {
        glyphData.clear();
        for (GlyphData glyph : newData) {
            glyphData.put(glyph.glyphId(), glyph);
        }
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!enabled) return;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (debugData.isEmpty() && glyphData.isEmpty()) return;

        BlockPos center = mc.player.blockPosition();
        int minY = Math.max(mc.level.getMinBuildHeight(), center.getY() - RADIUS);
        int maxY = Math.min(mc.level.getMaxBuildHeight(), center.getY() + RADIUS);

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer quadConsumer = bufferSource.getBuffer(RenderType.debugQuads());

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        int drawn = 0;

        // ponytail: RenderLevelStageEvent poseStack is fresh (identity) —
        // must translate by -camPos to get camera-relative space
        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        for (BlockPos pos : BlockPos.betweenClosed(
                center.getX() - RADIUS, minY, center.getZ() - RADIUS,
                center.getX() + RADIUS, maxY, center.getZ() + RADIUS)) {
            ElementConcentrations conc = debugData.get(pos);
            if (conc == null) continue;

            ElementConcentrations def = ElementBiomeProvider.getDefault(mc.level.getBiome(pos).value());
            int dominantIdx = findDominantElement(conc, def);
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

        renderElementLabels(mc, poseStack, bufferSource, camera, center, minY, maxY);
        renderGlyphLabels(mc, poseStack, bufferSource, camera);
    }

    private static void renderElementLabels(Minecraft mc, PoseStack poseStack,
                                            MultiBufferSource.BufferSource bufferSource,
                                            Camera camera, BlockPos center,
                                            int minY, int maxY) {
        Font font = mc.font;
        Vec3 camPos = camera.getPosition();

        for (BlockPos pos : BlockPos.betweenClosed(
                center.getX() - RADIUS, minY, center.getZ() - RADIUS,
                center.getX() + RADIUS, maxY, center.getZ() + RADIUS)) {
            ElementConcentrations conc = debugData.get(pos);
            if (conc == null) continue;

            ElementConcentrations def = ElementBiomeProvider.getDefault(mc.level.getBiome(pos).value());
            if (!hasElementDelta(conc, def)) continue;

            long dominantValue = conc.values()[findDominantElement(conc, def)];
            float alpha = Math.clamp((float) dominantValue / (float) ALPHA_REFERENCE, 0f, 1f) * MAX_ALPHA;
            if (alpha < 0.02f) continue;

            poseStack.pushPose();
            poseStack.translate(
                    pos.getX() + 0.5 - camPos.x,
                    pos.getY() + ELEMENT_LABEL_Y_OFFSET - camPos.y,
                    pos.getZ() + 0.5 - camPos.z);
            poseStack.mulPose(camera.rotation());
            poseStack.scale(ELEMENT_LABEL_SCALE, -ELEMENT_LABEL_SCALE, ELEMENT_LABEL_SCALE);

            Matrix4f matrix = poseStack.last().pose();
            int row = 0;
            for (int i = 0; i < ElementType.COUNT; i++) {
                long value = conc.values()[i];
                long defaultValue = def.values()[i];
                if (value == defaultValue) continue;

                String text = elementValueText(i, value);
                float x = -font.width(text) / 2.0f;
                int color = value > defaultValue ? ELEMENT_ABOVE_COLOR : ELEMENT_BELOW_COLOR;
                font.drawInBatch(text, x, row++ * font.lineHeight, color, false, matrix, bufferSource,
                        Font.DisplayMode.SEE_THROUGH, 0x80000000, 0x00F000F0);
            }
            poseStack.popPose();
        }

        bufferSource.endBatch();
    }

    private static boolean hasElementDelta(ElementConcentrations conc, ElementConcentrations def) {
        for (int i = 0; i < ElementType.COUNT; i++) {
            if (conc.values()[i] != def.values()[i]) return true;
        }
        return false;
    }

    private static String elementValueText(int index, long value) {
        return ElementType.values()[index].name().substring(0, 3) + '=' + value;
    }

    private static void renderGlyphLabels(Minecraft mc, PoseStack poseStack,
                                          MultiBufferSource.BufferSource bufferSource,
                                          Camera camera) {
        if (glyphData.isEmpty()) return;

        Font font = mc.font;
        Vec3 camPos = camera.getPosition();
        Vec3 playerCenter = Vec3.atCenterOf(mc.player.blockPosition());

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());
        for (GlyphData glyph : glyphData.values()) {
            Vec3 labelPos = glyphLabelPosition(glyph);
            if (labelPos.distanceTo(playerCenter) > RADIUS + 4) continue;
            renderGlyphArrow(poseStack, lineConsumer, glyph, labelPos);
        }
        poseStack.popPose();

        for (GlyphData glyph : glyphData.values()) {
            Vec3 labelPos = glyphLabelPosition(glyph);
            if (labelPos.distanceTo(playerCenter) > RADIUS + 4) continue;

            String text = glyph.symbolId().getPath()
                    + " " + String.format("%.3f", glyph.confidence())
                    + " " + String.format("%.2fx%.2f", glyph.length(), glyph.width());
            poseStack.pushPose();
            poseStack.translate(labelPos.x - camPos.x, labelPos.y - camPos.y, labelPos.z - camPos.z);
            poseStack.mulPose(camera.rotation());
            poseStack.scale(0.025f, -0.025f, 0.025f);

            Matrix4f matrix = poseStack.last().pose();
            float x = -font.width(text) / 2.0f;
            font.drawInBatch(text, x, 0, 0xFFFFFFFF, false, matrix, bufferSource,
                    Font.DisplayMode.SEE_THROUGH, 0x80000000, 0x00F000F0);
            poseStack.popPose();
        }

        bufferSource.endBatch();
    }

    private static void renderGlyphArrow(PoseStack poseStack, VertexConsumer consumer,
                                         GlyphData glyph, Vec3 start) {
        if (glyph.front().lengthSqr() < 1e-8) return;

        Vec3 front = glyph.front().normalize();
        double arrowLen = Math.clamp(glyph.length(), GLYPH_ARROW_MIN, GLYPH_ARROW_MAX);
        Vec3 end = start.add(front.scale(arrowLen));

        Vec3 normal = Vec3.atLowerCornerOf(glyph.face().getNormal());
        Vec3 side = cross(normal, front);
        if (side.lengthSqr() < 1e-8) return;
        side = side.normalize();

        double headLen = Math.min(0.18, arrowLen * 0.35);
        double headWidth = headLen * 0.65;
        Vec3 base = end.subtract(front.scale(headLen));
        addLine(poseStack, consumer, start, end);
        addLine(poseStack, consumer, end, base.add(side.scale(headWidth)));
        addLine(poseStack, consumer, end, base.subtract(side.scale(headWidth)));
    }

    private static void addLine(PoseStack poseStack, VertexConsumer consumer, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1e-8) return;
        dir = dir.normalize();
        consumer.addVertex(poseStack.last(), (float) from.x, (float) from.y, (float) from.z)
                .setColor(0xFF00FFFF)
                .setNormal(poseStack.last(), (float) dir.x, (float) dir.y, (float) dir.z);
        consumer.addVertex(poseStack.last(), (float) to.x, (float) to.y, (float) to.z)
                .setColor(0xFF00FFFF)
                .setNormal(poseStack.last(), (float) dir.x, (float) dir.y, (float) dir.z);
    }

    private static Vec3 cross(Vec3 a, Vec3 b) {
        return new Vec3(
                a.y * b.z - a.z * b.y,
                a.z * b.x - a.x * b.z,
                a.x * b.y - a.y * b.x
        );
    }

    private static Vec3 glyphLabelPosition(GlyphData glyph) {
        Vec3 normal = Vec3.atLowerCornerOf(glyph.face().getNormal());
        return glyph.center().add(normal.scale(LABEL_FACE_OFFSET));
    }

    private static int findDominantElement(ElementConcentrations conc, ElementConcentrations biomeDefault) {
        int dominant = 0;
        long maxExcess = Long.MIN_VALUE;
        for (int i = 0; i < ElementType.COUNT; i++) {
            long excess = conc.values()[i] - biomeDefault.values()[i];
            if (excess > maxExcess) {
                maxExcess = excess;
                dominant = i;
            }
        }
        return dominant;
    }
}
