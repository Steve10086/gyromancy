package com.astune.gyromancy.client.canvas;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.canvas.CanvasEntity;
import com.astune.gyromancy.canvas.CanvasScrollGeometry;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.entity.projection.ProjectionCanvasEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Renders the paper and strokes as one server-synchronized texture. */
public final class CanvasEntityRenderer extends EntityRenderer<CanvasEntity> {
    private static final ModelResourceLocation COLLAPSED_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "entity/canvas_plot"));

    public CanvasEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(CanvasEntity entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        if (entity instanceof ProjectionCanvasEntity) {
            super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
            return;
        }

        poseStack.pushPose();
        if (entity.isCollapsed()) {
            renderCollapsedModel(entity, poseStack, buffers, packedLight);
            poseStack.popPose();
            super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
            return;
        }
        applyAttachmentOrientation(entity, poseStack);

        CanvasDocument document = CanvasClientState.document(entity.getId());
        ResourceLocation texture = CanvasClientState.textureLocation(entity);
        if (document == null || texture == null) {
            poseStack.popPose();
            return;
        }

        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        PoseStack.Pose pose = poseStack.last();
        // Use the same document that produced the texture so the rendered quad
        // always matches the canvas' physical block dimensions and aspect ratio.
        float halfWidth = document.physicalWidth() * 0.5F;
        float halfHeight = document.physicalHeight() * 0.5F;
        float z = -CanvasEntity.DEPTH * 0.501F;
        texturedQuad(
                pose,
                consumer,
                -halfWidth,
                -halfHeight,
                halfWidth,
                halfHeight,
                z,
                packedLight);
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
    }

    private static void applyAttachmentOrientation(CanvasEntity entity,
                                                   PoseStack poseStack) {
        poseStack.mulPose(CanvasScrollGeometry.quadRotation(
                entity.surfaceWidthAxis(),
                entity.surfaceHeightAxis(),
                entity.surfaceNormal()));
    }

    /** Renders the user-supplied Blockbench scroll with its authored texture. */
    private static void renderCollapsedModel(CanvasEntity entity, PoseStack poseStack,
                                             MultiBufferSource buffers,
                                             int packedLight) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(COLLAPSED_MODEL);
        poseStack.pushPose();
        // Preserve the Blockbench origin. Only orient its Z axis vertically,
        // then stretch that axis to the physical height of this canvas.
        poseStack.mulPose(CanvasScrollGeometry.rotation(
                entity.getDirection(), entity.surfaceWidthAxis(), entity.surfaceHeightAxis()));
        poseStack.scale(1.0F, 1.0F, CanvasScrollGeometry.lengthScale(entity.syncedHeight()));
        VertexConsumer consumer = buffers.getBuffer(
                RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        Minecraft.getInstance().getItemRenderer().renderModelLists(
                model, ItemStack.EMPTY, packedLight, OverlayTexture.NO_OVERLAY, poseStack, consumer);
        poseStack.popPose();
    }

    /** Registers the standalone model so it is available through ModelManager. */
    public static ModelResourceLocation collapsedModel() {
        return COLLAPSED_MODEL;
    }

    private static void texturedQuad(PoseStack.Pose pose,
                                     VertexConsumer consumer,
                                     float left,
                                     float bottom,
                                     float right,
                                     float top,
                                     float z,
                                     int packedLight) {
        vertex(pose, consumer, right, bottom, z, 1.0F, 1.0F, packedLight);
        vertex(pose, consumer, left, bottom, z, 0.0F, 1.0F, packedLight);
        vertex(pose, consumer, left, top, z, 0.0F, 0.0F, packedLight);
        vertex(pose, consumer, right, top, z, 1.0F, 0.0F, packedLight);
    }

    private static void vertex(PoseStack.Pose pose,
                               VertexConsumer consumer,
                               float x,
                               float y,
                               float z,
                               float u,
                               float v,
                               int packedLight) {
        consumer.addVertex(pose, x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0.0F, 0.0F, -1.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(CanvasEntity entity) {
        return null;
    }
}
