package com.astune.gyromancy.client.entity;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.client.effect.EntityEffect;
import com.astune.gyromancy.client.effect.VortexOrbitEffect;
import com.astune.gyromancy.entity.ball.FireballEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

import static java.lang.Math.max;

public class FireballRenderer extends EntityRenderer<FireballEntity> {
    private static final ResourceLocation FIRE_BALL_FX =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "fire_ball");
    private static final ResourceLocation SMALL_FIRE_BALL_FX =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "fire_ball_small");
    private static final ResourceLocation SURROUNDING_FIRE_FX =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "surrounding_fire");
    private static final float MODEL_Y_OFFSET = 0.5F;
    private static final double PARTICLE_PLANE_SIZE = 0.3;
    private static final double MIN_PARTICLE_SCALE = 0.1;
    private static final double PARTICLE_SCALE_RANGE = 1;
    private static final double MIN_SPARK_SIZE = 1;
    private static final double SMALL_SIZE = 0.5;
    protected static final double RENDER_SCALE = 0.6;
    private static final Vec3 UP_AXIS = new Vec3(0, 1, 0);

    private final Map<FireballEntity, Integer> lastParticleTick = new WeakHashMap<>();
    private final Map<FireballEntity, VortexOrbitEffect> vortexEffects = new WeakHashMap<>();
    private final Map<FireballEntity, EntityEffect> bodyEffects = new WeakHashMap<>();
    private final Map<FireballEntity, FireballRenderColors.ColorTransition> elementColors = new WeakHashMap<>();

    public FireballRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(FireballEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        if (!entity.isAlive()) {
            cleanup(entity);
            return;
        }

        float size = max(0.1F, entity.getBallSize());
        boolean growing = !entity.isFullyGrown();

        // ── body: EntityEffect with fire_ball.fx ──
        EntityEffect body = bodyEffects.get(entity);
        if (body == null) {
            body = entity.getTargetBallSize() > SMALL_SIZE ? new EntityEffect(entity, FIRE_BALL_FX) : new EntityEffect(entity, SMALL_FIRE_BALL_FX);
            body.setSize((float) (size * RENDER_SCALE))
                    .setOffset(0, size * MODEL_Y_OFFSET, 0);
            body.setColor(FireballRenderColors.elementColor(entity, partialTick, elementColors));
            body.start();
            bodyEffects.put(entity, body);
        }
        else if (growing)  {
            body.setSize((float) (size * RENDER_SCALE)).setOffset(0, size * MODEL_Y_OFFSET, 0);
            body.setColor(FireballRenderColors.elementColor(entity, partialTick, elementColors));
            body.tick();
        } else {
            body.setColor(FireballRenderColors.elementColor(entity, partialTick, elementColors));
            body.tick();
        }

        // ── spawn vortex: VortexOrbitEffect during growth ──
        VortexOrbitEffect vortex = vortexEffects.get(entity);
        float vortexSize = 1f;
        if (vortex == null && growing && entity.getTargetBallSize() > MIN_SPARK_SIZE) {
            float targetSize = Math.max(0.1F, entity.getTargetBallSize());
            vortex = new VortexOrbitEffect(SURROUNDING_FIRE_FX, entity.level(),
                    entity::position, 30, 3f, 4f, 60, UP_AXIS, targetSize, 20)
                    .setOffset(0, targetSize * MODEL_Y_OFFSET, 0)
                    .setSize(vortexSize)
                    .setSpeed((float) (max(1, 1/max(0.5, entity.getTargetBallSize() - 1))))
                    .setAlive(entity::isAlive);
            vortex.start();
            vortexEffects.put(entity, vortex);
        }
        if(vortex != null && !growing){
            vortex.setRate(0f);
        }
        if (vortex != null) {
            //vortex.setSize((float) (vortexSize * (max(0, 0.9 - entity.getFireballSize() / entity.getTargetFireballSize()))));
            vortex.tick();
        }

        // ── flame trail particles ──
        spawnFlameParticles(entity);
    }

    @Override
    public ResourceLocation getTextureLocation(FireballEntity entity) {
        return null;
    }

    private void cleanup(FireballEntity entity) {
        EntityEffect body = bodyEffects.remove(entity);
        if (body != null) body.stop();
        elementColors.remove(entity);
        VortexOrbitEffect vortex = vortexEffects.remove(entity);
        if (vortex != null) vortex.kill();
    }

    private void spawnFlameParticles(FireballEntity entity) {
        Vec3 velocity = entity.getDeltaMovement();
        if (velocity.lengthSqr() < 1.0E-8 || lastParticleTick.getOrDefault(entity, -1) == entity.tickCount) return;
        lastParticleTick.put(entity, entity.tickCount);

        RandomSource random = entity.getRandom();
        Vec3 normal = velocity.normalize();
        Vec3 right = stableAxis(normal);
        Vec3 up = normal.cross(right).normalize();
        double halfSide = entity.getBallSize() * PARTICLE_PLANE_SIZE * 0.5;
        int count = 2 + random.nextInt(3);

        for (int i = 0; i < count; i++) {
            Vec3 offset = right.scale((random.nextDouble() - 0.5) * 2.0 * halfSide)
                    .add(up.scale((random.nextDouble()) * 2.0 * halfSide));
            Vec3 pos = entity.position().add(offset);
            Particle particle = Minecraft.getInstance().particleEngine.createParticle(
                    ParticleTypes.FLAME, pos.x, pos.y, pos.z,
                    velocity.x * 0.02, velocity.y * 0.02, velocity.z * 0.02);
            if (particle != null) {
                particle.scale((float)(entity.getBallSize()
                        * (MIN_PARTICLE_SCALE + random.nextDouble() * PARTICLE_SCALE_RANGE)));
            }
        }
    }

    private static Vec3 stableAxis(Vec3 normal) {
        Vec3 fallback = Math.abs(normal.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        return fallback.subtract(normal.scale(fallback.dot(normal))).normalize();
    }
}
