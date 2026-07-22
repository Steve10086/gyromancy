package com.astune.gyromancy.client.entity;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.client.effect.EntityEffect;
import com.astune.gyromancy.entity.ball.OldFireballEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.WeakHashMap;

import static com.astune.gyromancy.client.entity.FireballRenderer.RENDER_SCALE;
import static java.lang.Math.max;

public class OldFireballRenderer extends EntityRenderer<OldFireballEntity> {
    private static final ResourceLocation FIRE_BALL_FX =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "fire_ball_old");
    private static final float MODEL_Y_OFFSET = 0.5F;

    private final Map<OldFireballEntity, EntityEffect> bodyEffects = new WeakHashMap<>();
    private final Map<OldFireballEntity, FireballRenderColors.ColorTransition> elementColors = new WeakHashMap<>();

    public OldFireballRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(OldFireballEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        if (!entity.isAlive()) {
            EntityEffect body = bodyEffects.remove(entity);
            if (body != null) body.stop();
            elementColors.remove(entity);
            return;
        }

        float size = max(0.1F, entity.getBallSize());

        // ── body: EntityEffect with fire_ball.fx ──
        EntityEffect body = bodyEffects.get(entity);
        if (body == null) {
            body = new EntityEffect(entity, FIRE_BALL_FX)
                    .setSize((float) (size * RENDER_SCALE))
                    .setOffset(0, size * MODEL_Y_OFFSET, 0);
            body.setColor(color(entity, partialTick));
            body.start();
            bodyEffects.put(entity, body);
        } else {
            body.setSize((float) (size * RENDER_SCALE)).setOffset(0, size * MODEL_Y_OFFSET, 0);
            body.setColor(color(entity, partialTick));
            body.tick();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(OldFireballEntity entity) {
        return null;
    }

    private int color(OldFireballEntity entity, float partialTick) {
        return entity.isDebug()
                ? FireballRenderColors.debugColor(entity, partialTick)
                : FireballRenderColors.elementColor(entity, entity.getSyncedAverageElementLevel(),
                        partialTick, elementColors);
    }
}
