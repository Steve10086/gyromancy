package com.astune.gyromancy.client.entity;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.client.effect.EntityEffect;
import com.astune.gyromancy.client.effect.VortexOrbitEffect;
import com.astune.gyromancy.entity.ball.FireballEntity;
import com.astune.gyromancy.network.FireballStateEventPacket;
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
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.WeakHashMap;

import static java.lang.Math.max;

public class FireballRenderer extends EntityRenderer<FireballEntity> {
    private static final Set<FireballRenderer> INSTANCES =
            Collections.newSetFromMap(new IdentityHashMap<>());
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
    private final Map<FireballEntity, EntityEffect> smallBodyEffects = new WeakHashMap<>();
    private final Map<FireballEntity, EntityEffect> overlayEffects = new WeakHashMap<>();
    private final Map<FireballEntity, FireballRenderColors.ColorTransition> elementColors = new WeakHashMap<>();
    private final Set<FireballEntity> terminalEntities = Collections.newSetFromMap(new WeakHashMap<>());

    public FireballRenderer(EntityRendererProvider.Context context) {
        super(context);
        INSTANCES.add(this);
    }

    public static void clearClientState() {
        for (FireballRenderer renderer : Set.copyOf(INSTANCES)) renderer.clearEffects();
    }

    /** Applies an authoritative terminal-state input, even if the tracked entity was already removed. */
    public static void onStateEvent(FireballStateEventPacket packet) {
        var client = Minecraft.getInstance();
        Level level = client.level;
        if (level == null) return;

        var tracked = level.getEntity(packet.entityId());
        if (tracked instanceof FireballEntity fireball) {
            FireballRenderer selected = null;
            for (FireballRenderer renderer : INSTANCES) {
                if (selected == null || renderer.smallBodyEffects.containsKey(fireball)) selected = renderer;
                if (renderer.smallBodyEffects.containsKey(fireball)) break;
            }
            if (selected != null) {
                selected.beginStateEvent(fireball, packet);
                return;
            }
        }

        EntityEffect effect = new EntityEffect(level, packet.position(), FIRE_BALL_FX)
                .setSize((float) (Math.max(0.1F, packet.ballSize()) * RENDER_SCALE))
                .setOffset(0, Math.max(0.1F, packet.ballSize()) * MODEL_Y_OFFSET, 0)
                .setColor(FireballRenderColors.elementColorForAverage(packet.averageElementLevel()));
        effect.start();
        if (effect.sendEventAndDetach(packet.eventName(), packet.position()) == 0) effect.stop();
    }

    private void clearEffects() {
        smallBodyEffects.values().forEach(EntityEffect::stop);
        overlayEffects.values().forEach(EntityEffect::stop);
        vortexEffects.values().forEach(VortexOrbitEffect::kill);
        smallBodyEffects.clear();
        overlayEffects.clear();
        vortexEffects.clear();
        lastParticleTick.clear();
        elementColors.clear();
        terminalEntities.clear();
    }

    @Override
    public void render(FireballEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        if (!entity.isAlive()) {
            cleanup(entity);
            return;
        }
        if (terminalEntities.contains(entity)) return;

        float size = max(0.1F, entity.getBallSize());
        boolean growing = !entity.isFullyGrown();

        // The small effect is the persistent base layer at every size.
        EntityEffect smallBody = smallBodyEffects.get(entity);
        if (smallBody == null) {
            smallBody = new EntityEffect(entity, SMALL_FIRE_BALL_FX);
            smallBody.setSize((float) (size * RENDER_SCALE))
                    .setOffset(0, size * MODEL_Y_OFFSET, 0)
                    .setColor(FireballRenderColors.elementColor(entity, partialTick, elementColors));
            smallBody.start();
            smallBodyEffects.put(entity, smallBody);
        }
        if (!smallBody.isDetached()) {
            smallBody.setSize((float) (size * RENDER_SCALE)).setOffset(0, size * MODEL_Y_OFFSET, 0);
            smallBody.setColor(FireballRenderColors.elementColor(entity, partialTick, elementColors));
            smallBody.tick();
        }

        // Larger fireballs add the stateful effect over the persistent small layer.
        if (size > SMALL_SIZE) {
            EntityEffect overlay = overlayEffects.get(entity);
            if (overlay == null) {
                overlay = new EntityEffect(entity, FIRE_BALL_FX);
                overlay.setSize((float) (size * RENDER_SCALE))
                        .setOffset(0, size * MODEL_Y_OFFSET, 0)
                        .setColor(FireballRenderColors.elementColor(entity, partialTick, elementColors));
                overlay.start();
                overlayEffects.put(entity, overlay);
            }
            if (!overlay.isDetached()) {
                overlay.setSize((float) (size * RENDER_SCALE)).setOffset(0, size * MODEL_Y_OFFSET, 0);
                overlay.setColor(FireballRenderColors.elementColor(entity, partialTick, elementColors));
                overlay.tick();
            }
        } else {
            EntityEffect overlay = overlayEffects.remove(entity);
            if (overlay != null && !overlay.isDetached()) overlay.stop();
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
        EntityEffect smallBody = smallBodyEffects.remove(entity);
        if (smallBody != null && !smallBody.isDetached()) smallBody.stop();
        EntityEffect overlay = overlayEffects.remove(entity);
        if (overlay != null && !overlay.isDetached()) overlay.stop();
        elementColors.remove(entity);
        terminalEntities.remove(entity);
        VortexOrbitEffect vortex = vortexEffects.remove(entity);
        if (vortex != null) vortex.kill();
    }

    private void beginStateEvent(FireballEntity entity, FireballStateEventPacket packet) {
        if (!terminalEntities.add(entity)) return;

        float size = Math.max(0.1F, packet.ballSize());
        int targets = 0;

        EntityEffect smallBody = smallBodyEffects.get(entity);
        if (smallBody != null && !smallBody.isDetached()) {
            smallBody.setSize((float) (size * RENDER_SCALE))
                    .setOffset(0, size * MODEL_Y_OFFSET, 0)
                    .setColor(FireballRenderColors.elementColor(entity, 0, elementColors));
            targets += smallBody.sendEventAndDetach(packet.eventName(), packet.position());
            if (!smallBody.isDetached()) {
                smallBodyEffects.remove(entity);
                smallBody.stop();
            }
        }

        EntityEffect overlay = overlayEffects.get(entity);
        if (overlay != null && !overlay.isDetached()) {
            overlay.setSize((float) (size * RENDER_SCALE))
                    .setOffset(0, size * MODEL_Y_OFFSET, 0)
                    .setColor(FireballRenderColors.elementColor(entity, 0, elementColors));
            targets += overlay.sendEventAndDetach(packet.eventName(), packet.position());
            if (!overlay.isDetached()) {
                overlayEffects.remove(entity);
                overlay.stop();
            }
        }

        if (overlay == null && targets == 0) {
            EntityEffect terminal = new EntityEffect(entity.level(), packet.position(), FIRE_BALL_FX)
                    .setSize((float) (size * RENDER_SCALE))
                    .setOffset(0, size * MODEL_Y_OFFSET, 0)
                    .setColor(FireballRenderColors.elementColor(entity, 0, elementColors));
            terminal.start();
            if (terminal.sendEventAndDetach(packet.eventName(), packet.position()) == 0) terminal.stop();
        }

        VortexOrbitEffect vortex = vortexEffects.remove(entity);
        if (vortex != null) vortex.kill();
        lastParticleTick.remove(entity);
        elementColors.remove(entity);
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
