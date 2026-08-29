package com.astune.gyromancy.client.entity;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.client.effect.EntityEffect;
import com.astune.gyromancy.entity.ball.MagicBallEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import static com.astune.gyromancy.client.entity.FireballRenderer.RENDER_SCALE;

public class ElementBallRenderer<T extends MagicBallEntity> extends EntityRenderer<T> {
    private static final float MODEL_Y_OFFSET = 0.5F;
    private static final Set<ElementBallRenderer<?>> INSTANCES =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final ResourceLocation effectId;
    private final int color;
    private final Map<T, EntityEffect> effects = new WeakHashMap<>();

    public ElementBallRenderer(EntityRendererProvider.Context context, String effect, int color) {
        super(context);
        this.effectId = ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, effect);
        this.color = color;
        INSTANCES.add(this);
    }

    public static void clearClientState() {
        for (ElementBallRenderer<?> renderer : Set.copyOf(INSTANCES)) {
            renderer.clearEffects();
        }
    }

    protected void clearEffects() {
        effects.values().forEach(EntityEffect::stop);
        effects.clear();
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        if (!entity.isAlive()) {
            EntityEffect effect = effects.remove(entity);
            if (effect != null) effect.stop();
            return;
        }
        float size = Math.max(0.1F, entity.getBallSize());
        EntityEffect effect = effects.get(entity);
        if (effect == null) {
            effect = new EntityEffect(entity, effectId)
                    .setSize((float)(size * RENDER_SCALE))
                    .setOffset(0, size * MODEL_Y_OFFSET, 0)
                    .setColor(color(entity));
            effect.start();
            effects.put(entity, effect);
        } else {
            effect.setSize((float)(size * RENDER_SCALE)).setOffset(0, size * MODEL_Y_OFFSET, 0).setColor(color(entity));
            effect.tick();
        }
    }

    protected int color(T entity) {
        return color;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return null;
    }
}
