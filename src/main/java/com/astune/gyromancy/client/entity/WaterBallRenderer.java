package com.astune.gyromancy.client.entity;

import com.astune.gyromancy.entity.ball.WaterBallEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.WeakHashMap;

public class WaterBallRenderer extends ElementBallRenderer<WaterBallEntity> {
    private static final int DEFAULT_COLOR = 0xFF4F9DFF;
    private static final int PARTICLES_PER_TICK = 3;
    private final Map<WaterBallEntity, Integer> lastParticleTicks = new WeakHashMap<>();

    public WaterBallRenderer(EntityRendererProvider.Context context) {
        super(context, "water_ball", DEFAULT_COLOR);
    }

    @Override
    protected void clearEffects() {
        super.clearEffects();
        lastParticleTicks.clear();
    }

    @Override
    public void render(WaterBallEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        spawnPotionParticles(entity);
    }

    @Override
    protected int color(WaterBallEntity entity) {
        PotionContents contents = entity.getPotionState().get(DataComponents.POTION_CONTENTS);
        if (contents == null || !contents.hasEffects()) return DEFAULT_COLOR;
        return 0xFF000000 | contents.getColor();
    }

    private void spawnPotionParticles(WaterBallEntity entity) {
        if (!entity.isAlive() || lastParticleTicks.getOrDefault(entity, -1) == entity.tickCount) return;
        lastParticleTicks.put(entity, entity.tickCount);

        int color = color(entity);
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        AABB box = entity.getBoundingBox();
        for (int i = 0; i < PARTICLES_PER_TICK; i++) {
            double x = box.minX + entity.getRandom().nextDouble() * box.getXsize();
            double y = box.minY + entity.getRandom().nextDouble() * box.getYsize();
            double z = box.minZ + entity.getRandom().nextDouble() * box.getZsize();
            Particle particle = Minecraft.getInstance().particleEngine.createParticle(
                    ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, color & 0xFFFFFF),
                    x, y, z, 0.0, 0.0, 0.0);
            if (particle != null) particle.setColor(red, green, blue);
        }
    }
}
