package com.astune.gyromancy.client.effect;

import com.astune.gyromancy.client.render.RenderAnimation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FlipbookEffect {
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final List<Effect> effects = new ArrayList<>();

    protected final ResourceLocation texture;
    protected final int frameCount;
    protected final float ticksPerFrame;
    protected final List<RenderAnimation.Action> actions;

    protected FlipbookEffect(ResourceLocation texture, int frameCount, float ticksPerFrame,
                             List<RenderAnimation.Action> actions) {
        this.texture = texture;
        this.frameCount = Math.max(1, frameCount);
        this.ticksPerFrame = Math.max(0.001F, ticksPerFrame);
        this.actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public static void spawn(Vec3 center, Vec3 normal, Vec3 up, ResourceLocation texture,
                             int frameCount, float ticksPerFrame, float size,
                             int lifetime, List<RenderAnimation.Action> actions) {
        spawn(center, normal, up, texture, frameCount, ticksPerFrame, size, size, lifetime, actions);
    }

    public static void spawn(Vec3 center, Vec3 normal, Vec3 up, ResourceLocation texture,
                             int frameCount, float ticksPerFrame, float width, float height,
                             int lifetime, List<RenderAnimation.Action> actions) {
        if (texture == null || lifetime <= 0 || width <= 0.0F || height <= 0.0F) return;
        Vec3 n = safeNormal(normal);
        Vec3 right = up.cross(n);
        if (right.lengthSqr() < 1e-8) right = fallbackUp(n).cross(n);
        right = right.normalize();
        Vec3 actualUp = n.cross(right).normalize();
        effects.add(new Effect(center, right, actualUp, n, texture, Math.max(1, frameCount),
                Math.max(0.001F, ticksPerFrame), width, height, lifetime, actions));
    }

    /** Clears effects owned by the client level being unloaded. */
    public static void clearAll() {
        effects.clear();
    }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 center, Vec3 normal, Vec3 up,
                       float ageTicks, float width, float height) {
        if (texture == null || width <= 0.0F || height <= 0.0F) return;
        Vec3 n = safeNormal(normal);
        Vec3 right = up.cross(n);
        if (right.lengthSqr() < 1e-8) right = fallbackUp(n).cross(n);
        right = right.normalize();
        render(center, right, n.cross(right).normalize(), n, texture, frameCount, ticksPerFrame,
                ageTicks, width, height, actions, renderType(texture), poseStack, bufferSource);
    }

    protected RenderType renderType(ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (effects.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            effects.clear();
            return;
        }

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        Iterator<Effect> it = effects.iterator();
        while (it.hasNext()) {
            Effect effect = it.next();
            if (effect.age++ >= effect.lifetime) {
                it.remove();
                continue;
            }
            render(effect.center, effect.right, effect.up, effect.normal, effect.texture, effect.frameCount,
                    effect.ticksPerFrame, effect.age, effect.width, effect.height, effect.actions,
                    RenderType.entityTranslucent(effect.texture), poseStack, bufferSource);
        }

        poseStack.popPose();
        bufferSource.endBatch();
    }

    static int frame(int age, int frameCount, float ticksPerFrame) {
        return ((int)(age / Math.max(0.001F, ticksPerFrame))) % Math.max(1, frameCount);
    }

    private static void render(Vec3 center, Vec3 rightAxis, Vec3 upAxis, Vec3 normal, ResourceLocation texture,
                               int frameCount, float ticksPerFrame, float ageTicks, float width, float height,
                               List<RenderAnimation.Action> actions, RenderType renderType,
                               PoseStack poseStack, MultiBufferSource bufferSource) {
        RenderAnimation.State animation = RenderAnimation.evaluate(ageTicks, actions);
        poseStack.pushPose();
        poseStack.translate(center.x, center.y, center.z);
        RenderAnimation.applyPose(poseStack, animation, false);
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer consumer = bufferSource.getBuffer(renderType);
        int frame = frame((int) ageTicks, frameCount, ticksPerFrame);
        float v0 = (float) frame / frameCount;
        float v1 = (float) (frame + 1) / frameCount;
        float halfWidth = width * animation.size.x * 0.5F;
        float halfHeight = height * animation.size.y * 0.5F;

        Vec3 right = rightAxis.scale(halfWidth);
        Vec3 up = upAxis.scale(halfHeight);
        Vec3 a = right.reverse().subtract(up);
        Vec3 b = right.subtract(up);
        Vec3 c = right.add(up);
        Vec3 d = up.subtract(right);

        addVertex(consumer, pose, a, 0.0F, v1, normal, animation);
        addVertex(consumer, pose, b, 1.0F, v1, normal, animation);
        addVertex(consumer, pose, c, 1.0F, v0, normal, animation);
        addVertex(consumer, pose, d, 0.0F, v0, normal, animation);
        poseStack.popPose();
    }

    private static void addVertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 pos,
                                  float u, float v, Vec3 normal, RenderAnimation.State animation) {
        consumer.addVertex(pose, (float) pos.x, (float) pos.y, (float) pos.z)
                .setColor(animation.argb())
                .setUv(u, v)
                .setOverlay(0)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static Vec3 safeNormal(Vec3 normal) {
        return normal.lengthSqr() < 1e-8 ? new Vec3(0, 1, 0) : normal.normalize();
    }

    private static Vec3 fallbackUp(Vec3 normal) {
        return Math.abs(normal.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
    }

    private static final class Effect {
        final Vec3 center;
        final Vec3 right;
        final Vec3 up;
        final Vec3 normal;
        final ResourceLocation texture;
        final int frameCount;
        final float ticksPerFrame;
        final float width;
        final float height;
        final int lifetime;
        final List<RenderAnimation.Action> actions;
        int age;

        Effect(Vec3 center, Vec3 right, Vec3 up, Vec3 normal, ResourceLocation texture,
               int frameCount, float ticksPerFrame, float width, float height,
               int lifetime, List<RenderAnimation.Action> actions) {
            this.center = center;
            this.right = right;
            this.up = up;
            this.normal = normal;
            this.texture = texture;
            this.frameCount = frameCount;
            this.ticksPerFrame = ticksPerFrame;
            this.width = width;
            this.height = height;
            this.lifetime = lifetime;
            this.actions = actions == null ? List.of() : List.copyOf(actions);
        }
    }
}
