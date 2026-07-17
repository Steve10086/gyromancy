package com.astune.gyromancy.client.entity;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.client.effect.EntityEffect;
import com.astune.gyromancy.entity.ball.OldFireballEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.Map;
import java.util.WeakHashMap;

import static com.astune.gyromancy.client.entity.FireballRenderer.RENDER_SCALE;
import static java.lang.Math.max;

public class OldFireballRenderer extends EntityRenderer<OldFireballEntity> {
    private static final ResourceLocation FIRE_BALL_FX =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "fire_ball_old");
    private static final float MODEL_Y_OFFSET = 0.5F;
    private static final int[] DEBUG_COLORS = {
            0xFFfc4339, 0xFFff7700, 0xFFffd900,
            0xFFD8D0C0, 0xFF80A5F3, 0xFFd000ff
    };
    private static final float DEBUG_COLOR_TICKS = 10.0F;
    private static final float ELEMENT_COLOR_TICKS = 10.0F;

    private final Map<OldFireballEntity, EntityEffect> bodyEffects = new WeakHashMap<>();
    private final Map<OldFireballEntity, ColorTransition> elementColors = new WeakHashMap<>();

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
            body.setColor(entity.isDebug() ? debugColor(entity, partialTick) : elementColor(entity, partialTick));
            body.start();
            bodyEffects.put(entity, body);
        } else {
            body.setSize((float) (size * RENDER_SCALE)).setOffset(0, size * MODEL_Y_OFFSET, 0);
            body.setColor(entity.isDebug() ? debugColor(entity, partialTick) : elementColor(entity, partialTick));
            body.tick();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(OldFireballEntity entity) {
        return null;
    }

    private static int debugColor(OldFireballEntity entity, float partialTick) {
        float cycle = (entity.tickCount + partialTick) / DEBUG_COLOR_TICKS;
        int from = Mth.floor(cycle) % DEBUG_COLORS.length;
        int to = (from + 1) % DEBUG_COLORS.length;
        return lerpColor(DEBUG_COLORS[from], DEBUG_COLORS[to], cycle - Mth.floor(cycle));
    }

    private int elementColor(OldFireballEntity entity, float partialTick) {
        int target = elementTargetColor(entity);
        ColorTransition transition = elementColors.get(entity);
        if (transition == null) {
            elementColors.put(entity, new ColorTransition(target, target, entity.tickCount));
            return target;
        }
        if (transition.target != target) {
            transition = new ColorTransition(transition.color(entity.tickCount + partialTick), target, entity.tickCount);
            elementColors.put(entity, transition);
        }
        return transition.color(entity.tickCount + partialTick);
    }

    private static int elementTargetColor(OldFireballEntity entity) {
        float tier = (float) Math.sqrt(Math.max(0.0F, entity.getSyncedAverageElementLevel()) / 1000.0F);
        if (tier <= 1.0F) return DEBUG_COLORS[0];
        if (tier >= DEBUG_COLORS.length) return DEBUG_COLORS[DEBUG_COLORS.length - 1];

        int from = Mth.floor(tier) - 1;
        return lerpColor(DEBUG_COLORS[from], DEBUG_COLORS[from + 1], tier - Mth.floor(tier));
    }

    private static int lerpColor(int from, int to, float t) {
        int a = lerpChannel((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, t);
        int r = lerpChannel((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = lerpChannel((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = lerpChannel(from & 0xFF, to & 0xFF, t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int lerpChannel(int from, int to, float t) {
        return Mth.clamp(Math.round(Mth.lerp(t, from, to)), 0, 255);
    }

    private record ColorTransition(int from, int target, int startTick) {
        int color(float tick) {
            return lerpColor(from, target, Mth.clamp((tick - startTick) / ELEMENT_COLOR_TICKS, 0.0F, 1.0F));
        }
    }
}
