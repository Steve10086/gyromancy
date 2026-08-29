package com.astune.gyromancy.client.effect;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.client.canvas.CanvasClientState;
import com.astune.gyromancy.client.canvas.ProjectionCanvasRenderPose;
import com.astune.gyromancy.client.compat.iris.IrisRenderBridge;
import com.lowdragmc.lowdraglib2.client.shader.HDRTarget;
import com.astune.gyromancy.entity.projection.ProjectionCanvasEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.vertex.VertexArray;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** Veil-backed additive glow pass for wand projection surfaces only. */
public final class WandProjectionGlowRenderer {
    private static final ResourceLocation RENDER_TYPE =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "canvas_projection_glow");
    private static final ResourceLocation SHADER =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "canvas_projection_glow");

    private static VertexArray vertexArray;
    private static HDRTarget irisOutputFbo;
    private static int irisOutputWidth = -1;
    private static int irisOutputHeight = -1;
    private static IrisRenderBridge.Target lastIrisTarget;
    private static boolean warnedMissingShader;

    private WandProjectionGlowRenderer() {}

    /** Releases the copy target and mesh VAO owned by the current world pass. */
    public static void clearRenderResources() {
        if (vertexArray != null) {
            vertexArray.free();
            vertexArray = null;
        }
        if (irisOutputFbo != null) irisOutputFbo.destroyBuffers();
        irisOutputFbo = null;
        irisOutputWidth = -1;
        irisOutputHeight = -1;
    }

    /** Called when Minecraft unloads a client level (including a dimension change). */
    public static void onClientLevelUnload() {
        clearRenderResources();
        lastIrisTarget = null;
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        IrisRenderBridge.Target irisTarget = IrisRenderBridge.currentTarget();
        if (!java.util.Objects.equals(lastIrisTarget, irisTarget)) {
            clearRenderResources();
            lastIrisTarget = irisTarget;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof ProjectionCanvasEntity projection)) continue;

            CanvasDocument document = CanvasClientState.document(projection.getId());
            ResourceLocation texture = CanvasClientState.textureLocation(projection);
            if (document != null && texture != null) {
                render(projection, document, texture, partialTick, irisTarget);
            }
        }
    }

    private static void render(ProjectionCanvasEntity projection,
                               CanvasDocument document,
                               ResourceLocation texture,
                               float partialTick,
                               IrisRenderBridge.Target irisTarget) {
        ShaderProgram shader = VeilRenderSystem.renderer().getShaderManager().getShader(SHADER);
        RenderType renderType = VeilRenderType.get(RENDER_TYPE);
        if (shader == null || !shader.isValid() || renderType == null) {
            if (!warnedMissingShader) {
                warnedMissingShader = true;
                Gyromancy.LOGGER.warn("[Gyromancy] Wand projection glow shader {} is not loaded", SHADER);
            }
            return;
        }

        var frame = ProjectionCanvasRenderPose.frame(projection, partialTick);
        float entranceScale = projection.renderEntranceScale(partialTick);
        Vec3 offset = frame.normal().scale(com.astune.gyromancy.canvas.CanvasEntity.DEPTH * 0.501F);
        Vec3 center = frame.origin().add(offset);
        Vec3 halfU = frame.axisU().scale(document.physicalWidth() * 0.5F * entranceScale);
        Vec3 halfV = frame.axisV().scale(document.physicalHeight() * 0.5F * entranceScale);

        BufferBuilder builder = RenderSystem.renderThreadTesselator()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        addVertex(builder, center.add(halfU).subtract(halfV), 0.0F, 1.0F);
        addVertex(builder, center.subtract(halfU).subtract(halfV), 1.0F, 1.0F);
        addVertex(builder, center.subtract(halfU).add(halfV), 1.0F, 0.0F);
        addVertex(builder, center.add(halfU).add(halfV), 0.0F, 0.0F);

        MeshData mesh = builder.buildOrThrow();
        if (vertexArray == null) vertexArray = VertexArray.create();
        vertexArray.upload(mesh, VertexArray.DrawUsage.STREAM);
        HDRTarget outputFbo = null;
        if (irisTarget != null) {
            outputFbo = ensureIrisOutputFbo();
            if (outputFbo == null
                    || !IrisRenderBridge.copyColorAndDepthTo(irisTarget, outputFbo)) {
                return;
            }
            outputFbo.bindWrite(false);
        }

        boolean rendered = false;
        try {
            vertexArray.bind();
            vertexArray.setup(renderType);
            RenderSystem.setShaderTexture(0, texture);
            shader.bind();
            shader.setDefaultUniforms(VertexFormat.Mode.QUADS);
            shader.bindSamplers(0);
            if (irisTarget != null) outputFbo.bindWrite(false);
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
            RenderSystem.blendFunc(GL11.GL_ONE, GL11.GL_ONE);
            RenderSystem.depthMask(false);
            vertexArray.draw();
            rendered = true;
        } finally {
            shader.clearSamplers();
            ShaderProgram.unbind();
            vertexArray.clear(renderType);
            RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            VertexArray.unbind();
            if (irisTarget != null) {
                IrisRenderBridge.restoreMainFramebuffer();
            }
        }

        if (irisTarget != null && rendered && outputFbo != null) {
            IrisRenderBridge.blitTextureTo(
                    irisTarget, outputFbo.getColorTextureId());
        }
    }

    private static HDRTarget ensureIrisOutputFbo() {
        var main = Minecraft.getInstance().getMainRenderTarget();
        if (irisOutputFbo != null
                && irisOutputWidth == main.width
                && irisOutputHeight == main.height) {
            return irisOutputFbo;
        }

        if (irisOutputFbo != null) irisOutputFbo.destroyBuffers();
        irisOutputFbo = new HDRTarget(main.width, main.height, GL11.GL_NEAREST, true);
        irisOutputWidth = main.width;
        irisOutputHeight = main.height;
        return irisOutputFbo;
    }

    private static void addVertex(BufferBuilder builder, Vec3 position, float u, float v) {
        builder.addVertex((float) position.x, (float) position.y, (float) position.z)
                .setUv(u, v)
                .setColor(255, 255, 255, 255);
    }
}
