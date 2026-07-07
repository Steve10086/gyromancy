package com.astune.gyromancy.client.entity;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.client.render.FrameAnimation;
import com.astune.gyromancy.client.render.ObjFrameModel;
import com.astune.gyromancy.entity.FireballEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.ResourceLocation;

public class FireballRenderer extends ThrownItemRenderer<FireballEntity> {
    private static final ResourceLocation MODEL_PATH =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "models/entity/fireball");
    private static final ResourceLocation FALLBACK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "models/entity/fireball/texture.png");
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final float MODEL_UNIT_SCALE = 1.5F;
    private static final float MODEL_Y_OFFSET = 0.5F;
    private ObjFrameModel model;
    private FrameAnimation animation;

    public FireballRenderer(EntityRendererProvider.Context context) {
        super(context, 1.0F, true);
    }

    @Override
    public void render(FireballEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        ObjFrameModel model = model();
        if (model.hasFrames()) {
            poseStack.pushPose();
            float scale = Math.max(0.1F, entity.getFireballSize()) * MODEL_UNIT_SCALE;
            poseStack.translate(0.0F, MODEL_Y_OFFSET / MODEL_UNIT_SCALE * scale, 0.0F);
            poseStack.scale(scale, scale, scale);

            float ageTicks = entity.tickCount + partialTick;
            model.renderFrame(animation().frame(ageTicks), ageTicks, poseStack.last(), bufferSource,
                    FULL_BRIGHT, FALLBACK_TEXTURE);

            poseStack.popPose();
            return;
        }

        poseStack.pushPose();
        float scale = Math.max(0.1F, entity.getFireballSize());
        poseStack.scale(scale, scale, scale);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
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
}
