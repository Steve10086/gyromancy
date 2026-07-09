package com.astune.gyromancy.client.entity;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.client.effect.FireballSpawnEffect;
import com.astune.gyromancy.client.render.FrameAnimation;
import com.astune.gyromancy.client.render.ObjFrameModel;
import com.astune.gyromancy.client.render.RenderAnimation;
import com.astune.gyromancy.entity.FireballEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

import static java.lang.Math.min;

public class FireballRenderer extends ThrownItemRenderer<FireballEntity> {
    private static final ResourceLocation MODEL_PATH =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "models/entity/fireball");
    private static final ResourceLocation FALLBACK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "models/entity/fireball/texture.png");
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final float MODEL_UNIT_SCALE = 1.5F;
    private static final float MODEL_Y_OFFSET = 0.5F;
    private static final double PARTICLE_PLANE_SIZE = 0.3;
    private static final double MIN_PARTICLE_SCALE = 0.1;
    private static final double PARTICLE_SCALE_RANGE = 1;
    private ObjFrameModel model;
    private FrameAnimation animation;
    private final Map<FireballEntity, Integer> lastParticleTick = new WeakHashMap<>();

    public FireballRenderer(EntityRendererProvider.Context context) {
        super(context, 1.0F, true);
    }

    @Override
    public void render(FireballEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        ObjFrameModel model = model();
        float ageTicks = entity.tickCount + partialTick;
        float scale = Math.max(0.1F, entity.getFireballSize()) * MODEL_UNIT_SCALE;
        boolean renderSpawnEffect = !entity.isFullyGrown();
        Vec3 effectCenter = entity.getPosition(partialTick).add(0.0F, MODEL_Y_OFFSET / MODEL_UNIT_SCALE * scale, 0.0F);
        float effectAge = renderSpawnEffect ? ageTicks % Math.max(1, min(32, entity.getGrowthTicks())) : 0.0F;
        float effectSize = Math.max(0.1F, entity.getFireballSize());

        if (model.hasFrames()) {
            poseStack.pushPose();
            poseStack.translate(0.0F, MODEL_Y_OFFSET / MODEL_UNIT_SCALE * scale, 0.0F);
            poseStack.scale(scale, scale, scale);

            RenderAnimation.State renderAnimation = RenderAnimation.evaluate(ageTicks, java.util.List.of());
            RenderAnimation.applyPose(poseStack, renderAnimation);
            model.renderFrame(animation().frame(ageTicks), ageTicks, poseStack.last(), bufferSource,
                    FULL_BRIGHT, FALLBACK_TEXTURE, renderAnimation);

            poseStack.popPose();
            flush(bufferSource);
            if (renderSpawnEffect) {
                FireballSpawnEffect.INSTANCE.render(poseStack, bufferSource, effectCenter,
                        effectAge, effectSize, entity.getGrowthTicks());
            }
            spawnFlameParticles(entity);
            return;
        }

        poseStack.pushPose();
        scale = Math.max(0.1F, entity.getFireballSize());
        poseStack.scale(scale, scale, scale);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
        flush(bufferSource);
        if (renderSpawnEffect) {
            FireballSpawnEffect.INSTANCE.render(poseStack, bufferSource, effectCenter,
                    effectAge, effectSize, entity.getGrowthTicks());
        }
        spawnFlameParticles(entity);
    }

    private ObjFrameModel model() {
        if (model == null) {
            model = ObjFrameModel.load(MODEL_PATH);
        }
        return model;
    }

    private FrameAnimation animation() {
        if (animation == null) {
            animation = createAnimation(model().frameCount());
        }
        return animation;
    }

    private static FrameAnimation createAnimation(int frameCount) {
        return FrameAnimation.loop(frameCount, 1.0F);
    }

    private static void flush(MultiBufferSource bufferSource) {
        if (bufferSource instanceof MultiBufferSource.BufferSource buffer) {
            buffer.endBatch();
        }
    }

    private void spawnFlameParticles(FireballEntity entity) {
        Vec3 velocity = entity.getDeltaMovement();
        if (velocity.lengthSqr() < 1.0E-8 || lastParticleTick.getOrDefault(entity, -1) == entity.tickCount) return;
        lastParticleTick.put(entity, entity.tickCount);

        RandomSource random = entity.getRandom();
        Vec3 normal = velocity.normalize();
        Vec3 right = stableAxis(normal);
        Vec3 up = normal.cross(right).normalize();
        double halfSide = entity.getFireballSize() * PARTICLE_PLANE_SIZE * 0.5;
        int count = 2 + random.nextInt(3);

        for (int i = 0; i < count; i++) {
            Vec3 offset = right.scale((random.nextDouble() - 0.5) * 2.0 * halfSide)
                    .add(up.scale((random.nextDouble()) * 2.0 * halfSide));
            Vec3 pos = entity.position().add(offset);
            Particle particle = Minecraft.getInstance().particleEngine.createParticle(
                    ParticleTypes.FLAME, pos.x, pos.y, pos.z,
                    velocity.x * 0.02, velocity.y * 0.02, velocity.z * 0.02);
            if (particle != null) {
                particle.scale((float)(entity.getFireballSize()
                        * (MIN_PARTICLE_SCALE + random.nextDouble() * PARTICLE_SCALE_RANGE)));
            }
        }
    }

    private static Vec3 stableAxis(Vec3 normal) {
        Vec3 fallback = Math.abs(normal.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        return fallback.subtract(normal.scale(fallback.dot(normal))).normalize();
    }
}
