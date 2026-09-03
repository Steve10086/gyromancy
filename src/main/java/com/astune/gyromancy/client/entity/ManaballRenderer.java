package com.astune.gyromancy.client.entity;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.client.effect.EntityEffect;
import com.astune.gyromancy.client.effect.VortexOrbitEffect;
import com.astune.gyromancy.entity.ball.ManaballEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.WeakHashMap;

import static com.astune.gyromancy.client.entity.FireballRenderer.RENDER_SCALE;
import static java.lang.Math.max;

public class ManaballRenderer extends EntityRenderer<ManaballEntity> {
    private static final Set<ManaballRenderer> INSTANCES =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final ResourceLocation MANA_BALL_FX =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "mana_ball");
    private static final ResourceLocation SURROUNDING_MANA_FX =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "mana_particle");
    private static final float MODEL_Y_OFFSET = 0.5F;
    private static final Vec3 UP_AXIS = new Vec3(0, 1, 0);
    private final Map<ManaballEntity, EntityEffect> bodyEffects = new WeakHashMap<>();
    private final Map<ManaballEntity, VortexOrbitEffect> vortexEffects = new WeakHashMap<ManaballEntity, VortexOrbitEffect>();

    public ManaballRenderer(EntityRendererProvider.Context context) {
        super(context);
        INSTANCES.add(this);
    }

    public static void clearClientState() {
        for (ManaballRenderer renderer : Set.copyOf(INSTANCES)) renderer.clearEffects();
    }

    private void clearEffects() {
        bodyEffects.values().forEach(EntityEffect::stop);
        vortexEffects.values().forEach(VortexOrbitEffect::kill);
        bodyEffects.clear();
        vortexEffects.clear();
    }

    @Override
    public void render(ManaballEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        if (!entity.isAlive()) {
            EntityEffect body = bodyEffects.remove(entity);
            if (body != null) body.stop();
            VortexOrbitEffect vortex = vortexEffects.remove(entity);
            if (vortex != null) vortex.kill();
            return;
        }

        float size = max(0.1F, entity.getFieldSize());

        // ── body: EntityEffect with fire_ball.fx ──
        EntityEffect body = bodyEffects.get(entity);
        if (body == null) {
            body = new EntityEffect(entity, MANA_BALL_FX)
                    .setSize((float) (size * RENDER_SCALE))
                    .setOffset(0, size * MODEL_Y_OFFSET, 0);
            body.start();
            bodyEffects.put(entity, body);
        } else {
            body.setSize((float) (size * RENDER_SCALE)).setOffset(0, size * MODEL_Y_OFFSET, 0);
            body.tick();
        }

        // ── spawn vortex: VortexOrbitEffect during growth ──
        VortexOrbitEffect vortex = vortexEffects.get(entity);
        if (vortex == null) {
            float targetSize = Math.max(0.1F, entity.getTargetFieldSize());
            vortex = new VortexOrbitEffect(SURROUNDING_MANA_FX, entity.level(),
                    entity::position, 30, 3f, 4f, 60, UP_AXIS, 1, 20)
                    .setOffset(0, targetSize * MODEL_Y_OFFSET, 0)
                    .setSpeed((float) (max(1, 1/max(0.5, entity.getTargetFieldSize() - 1))))
                    .setAlive(entity::isAlive);
            vortex.start();
            vortexEffects.put(entity, vortex);
        }

        vortex.tick();
    }

    @Override
    public ResourceLocation getTextureLocation(ManaballEntity entity) {
        return null;
    }
}
