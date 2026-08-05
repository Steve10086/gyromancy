package com.astune.gyromancy.client.canvas;

import com.astune.gyromancy.api.geometry.SurfaceFrame;
import com.astune.gyromancy.canvas.CanvasEntity;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Renders the paper and strokes as one server-synchronized texture. */
public final class CanvasEntityRenderer extends EntityRenderer<CanvasEntity> {
    public CanvasEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(CanvasEntity entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        CanvasDocument document = CanvasClientState.document(entity.getId());
        ResourceLocation texture = CanvasClientState.textureLocation(entity);
        if (document == null || texture == null) return;

        poseStack.pushPose();
        if (entity instanceof com.astune.gyromancy.wand.WandProjectionCanvasEntity projection) {
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(texture));
            SurfaceFrame renderFrame = projection.renderSurfaceFrame(partialTick);
            orientedTexturedQuad(
                    poseStack.last(), consumer,
                    renderFrame.axisU(), renderFrame.axisV(), renderFrame.normal(),
                    document.physicalWidth() * 0.5F,
                    document.physicalHeight() * 0.5F,
                    packedLight);
            poseStack.popPose();
            super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
            return;
        }
        if (entity.getDirection().getAxis().isVertical()) {
            float floorRotation = entity.getDirection() == net.minecraft.core.Direction.UP
                    ? 90.0F : -90.0F;
            poseStack.mulPose(Axis.XP.rotationDegrees(floorRotation));
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        } else {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
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

    private static void orientedTexturedQuad(PoseStack.Pose pose,
                                             VertexConsumer consumer,
                                             net.minecraft.world.phys.Vec3 axisU,
                                             net.minecraft.world.phys.Vec3 axisV,
                                             net.minecraft.world.phys.Vec3 normal,
                                             float halfWidth,
                                             float halfHeight,
                                             int packedLight) {
        net.minecraft.world.phys.Vec3 offset = normal.scale(CanvasEntity.DEPTH * 0.501F);
        // Entity canvas textures are pre-mirrored on X. The legacy attached-
        // canvas rotations compensate for that; this direct world-space quad
        // must reverse U explicitly so raster X and SurfaceFrame U agree.
        orientedVertex(pose, consumer, axisU, axisV, offset,
                halfWidth, -halfHeight, projectionTextureU(halfWidth), 1.0F,
                normal, packedLight);
        orientedVertex(pose, consumer, axisU, axisV, offset,
                -halfWidth, -halfHeight, projectionTextureU(-halfWidth), 1.0F,
                normal, packedLight);
        orientedVertex(pose, consumer, axisU, axisV, offset,
                -halfWidth, halfHeight, projectionTextureU(-halfWidth), 0.0F,
                normal, packedLight);
        orientedVertex(pose, consumer, axisU, axisV, offset,
                halfWidth, halfHeight, projectionTextureU(halfWidth), 0.0F,
                normal, packedLight);
    }

    static float projectionTextureU(float surfaceU) {
        return surfaceU >= 0.0F ? 0.0F : 1.0F;
    }

    private static void orientedVertex(PoseStack.Pose pose,
                                       VertexConsumer consumer,
                                       net.minecraft.world.phys.Vec3 axisU,
                                       net.minecraft.world.phys.Vec3 axisV,
                                       net.minecraft.world.phys.Vec3 offset,
                                       float uDistance,
                                       float vDistance,
                                       float textureU,
                                       float textureV,
                                       net.minecraft.world.phys.Vec3 normal,
                                       int packedLight) {
        net.minecraft.world.phys.Vec3 point = offset
                .add(axisU.scale(uDistance))
                .add(axisV.scale(vDistance));
        consumer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(0xFFFFFFFF)
                .setUv(textureU, textureV)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
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
