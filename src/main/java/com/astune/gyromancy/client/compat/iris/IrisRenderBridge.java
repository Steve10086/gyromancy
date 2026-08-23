package com.astune.gyromancy.client.compat.iris;

import com.astune.gyromancy.Gyromancy;
import com.lowdragmc.lowdraglib2.client.shader.HDRTarget;
import com.lowdragmc.lowdraglib2.client.shader.LDLibShaders;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL30;

import javax.annotation.Nullable;

/**
 * Keeps Gyromancy's Veil passes on the framebuffer currently used by Iris.
 *
 * <p>Iris owns its shader-pipeline framebuffer and may replace the vanilla target
 * during a world render. Instead of calling a private API from another rendering
 * mod, this bridge snapshots the framebuffer that is actually bound at the
 * NeoForge render stage. The Iris depth/color unlock remains reflective so Iris
 * stays an optional runtime mod.</p>
 */
public final class IrisRenderBridge {
    private static final String IRIS_CLASS = "net.irisshaders.iris.Iris";
    private static final String IRIS_EXTENDED_SHADER_CLASS =
            "net.irisshaders.iris.pipeline.programs.ExtendedShader";
    private static final String IRIS_DEPTH_COLOR_STORAGE =
            "net.irisshaders.iris.gl.blending.DepthColorStorage";

    private static boolean warnedMissingTarget;
    private static boolean warnedIrisReflectionFailure;
    private static boolean warnedDepthUnlockFailure;
    private static boolean warnedMissingBlitShader;
    private static boolean loggedTarget;
    private static Boolean irisLoaded;
    private static Target stageTarget;

    private IrisRenderBridge() {}

    /** Starts a new AFTER_PARTICLES render stage and forgets the previous Iris target. */
    public static void beginRenderStage() {
        stageTarget = null;
    }

    /**
     * Finds the framebuffer currently bound by Iris for this render stage.
     * Returns {@code null} for a normal Minecraft/Veil render path.
     */
    @Nullable
    public static Target currentTarget() {
        if (!isIrisLoaded()) return null;

        RenderSystem.assertOnRenderThread();
        if (stageTarget != null) return stageTarget;

        Target shaderTarget = targetFromIrisShader();
        if (shaderTarget != null) {
            stageTarget = shaderTarget;
            return shaderTarget;
        }

        Target boundTarget = targetFromBoundFramebuffer();
        if (boundTarget != null) stageTarget = boundTarget;
        return boundTarget;
    }

    /** Reads Iris' own current particle target without loading Photon classes. */
    @Nullable
    private static Target targetFromIrisShader() {
        ShaderInstance particleShader = GameRenderer.getParticleShader();
        if (particleShader == null) return null;

        try {
            Class<?> extendedShader = Class.forName(
                    IRIS_EXTENDED_SHADER_CLASS, false,
                    IrisRenderBridge.class.getClassLoader());
            if (!extendedShader.isInstance(particleShader)) return null;

            Object pipeline = readField(extendedShader, particleShader, "parent");
            boolean beforeTranslucent = readBooleanField(pipeline, "isBeforeTranslucent");
            String targetField = beforeTranslucent
                    ? "writingToBeforeTranslucent"
                    : "writingToAfterTranslucent";
            Object framebuffer = readField(extendedShader, particleShader, targetField);
            if (framebuffer == null) return null;

            int framebufferId = invokeInt(framebuffer, "getId");
            if (framebufferId <= 0
                    || framebufferId == AdvancedFbo.getMainFramebuffer().getId()) {
                return null;
            }

            Minecraft minecraft = Minecraft.getInstance();
            Target target = new Target(framebufferId,
                    minecraft.getMainRenderTarget().width,
                    minecraft.getMainRenderTarget().height,
                    invokeBoolean(framebuffer, "hasDepthAttachment"));
            if (!loggedTarget) {
                loggedTarget = true;
                Gyromancy.LOGGER.info(
                        "[Gyromancy] Iris target active: fbo={}, depth={}, source=ExtendedShader",
                        target.framebufferId(), target.hasDepthAttachment());
            }
            return target;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            if (!warnedIrisReflectionFailure) {
                warnedIrisReflectionFailure = true;
                Gyromancy.LOGGER.warn(
                        "[Gyromancy] Iris framebuffer fields are unavailable; "
                                + "falling back to the currently bound OpenGL framebuffer", exception);
            }
            return null;
        }
    }

    /** Fallback for Iris versions that do not expose ExtendedShader fields in the same shape. */
    @Nullable
    private static Target targetFromBoundFramebuffer() {
        int framebufferId = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int mainFramebufferId = AdvancedFbo.getMainFramebuffer().getId();
        if (framebufferId <= 0) return null;
        if (framebufferId == mainFramebufferId) return stageTarget;

        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getMainRenderTarget().width;
        int height = minecraft.getMainRenderTarget().height;
        boolean hasDepth = hasDepthAttachment(framebufferId);
        if (!isComplete(framebufferId)) {
            if (!warnedMissingTarget) {
                warnedMissingTarget = true;
                Gyromancy.LOGGER.warn(
                        "[Gyromancy] Iris has a non-vanilla framebuffer bound, but it is incomplete; "
                                + "custom Veil passes will use their normal target");
            }
            return null;
        }

        Target target = new Target(framebufferId, width, height, hasDepth);
        stageTarget = target;
        if (!loggedTarget) {
            loggedTarget = true;
            Gyromancy.LOGGER.info(
                    "[Gyromancy] Iris target active: fbo={}, depth={}, source=GL_DRAW_FRAMEBUFFER_BINDING",
                    framebufferId, hasDepth);
        }
        return target;
    }

    /**
     * Copies the selected Iris color/depth attachments into an effect FBO.
     *
     * <p>The pass renders into its own HDR target for the same reason: Iris owns
     * its pipeline FBO and may replace or re-lock state written directly to it.
     * Keeping the effect pass on an ordinary FBO also leaves the existing Veil
     * shader and blend path unchanged.</p>
     */
    public static boolean copyColorAndDepthTo(Target source, HDRTarget destination) {
        if (source == null) return false;

        RenderSystem.assertOnRenderThread();
        if (source.hasDepthAttachment()) {
            destination.copyDepthAndColorFrom(
                    source.framebufferId(), source.width(), source.height());
        } else {
            destination.copyColorFrom(source.framebufferId(), source.width(), source.height());
        }
        return true;
    }

    /** Copies only the selected Iris depth attachment into a Veil effect FBO. */
    public static boolean copyDepthTo(Target source, AdvancedFbo destination) {
        if (source == null || !source.hasDepthAttachment() || !destination.hasDepthAttachment()) {
            return false;
        }

        RenderSystem.assertOnRenderThread();
        int previousRead = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.framebufferId());
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, destination.getId());
            GL30.glBlitFramebuffer(
                    0, 0, source.width(), source.height(),
                    0, 0, destination.getWidth(), destination.getHeight(),
                    GL30.GL_DEPTH_BUFFER_BIT, GL30.GL_NEAREST);
            return true;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        }
    }

    /**
     * Shares the selected Iris depth texture with a Veil effect FBO.
     *
     * <p>This is the important part of the Iris path. Iris owns the depth
     * attachment and a framebuffer blit is not reliable while its pipeline is
     * active; attaching the same texture keeps the effect depth test identical
     * to the scene depth test without changing the effect geometry or quality.</p>
     */
    public static boolean shareDepthTo(Target source, AdvancedFbo destination) {
        if (source == null || !source.hasDepthAttachment()
                || !destination.hasDepthAttachment()) {
            return false;
        }

        RenderSystem.assertOnRenderThread();
        int previousFramebuffer = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, source.framebufferId());

            int attachment = GL30.GL_DEPTH_ATTACHMENT;
            int objectType = GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_FRAMEBUFFER, attachment,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            int depthTexture = GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_FRAMEBUFFER, attachment,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            if (objectType == GL30.GL_NONE) {
                attachment = GL30.GL_DEPTH_STENCIL_ATTACHMENT;
                objectType = GL30.glGetFramebufferAttachmentParameteri(
                        GL30.GL_FRAMEBUFFER, attachment,
                        GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
                depthTexture = GL30.glGetFramebufferAttachmentParameteri(
                        GL30.GL_FRAMEBUFFER, attachment,
                        GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            }

            if (objectType != GL30.GL_TEXTURE || depthTexture <= 0) {
                return false;
            }

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, destination.getId());
            GL30.glFramebufferTexture2D(
                    GL30.GL_FRAMEBUFFER, attachment, GL30.GL_TEXTURE_2D, depthTexture, 0);
            return GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)
                    == GL30.GL_FRAMEBUFFER_COMPLETE;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
        }
    }

    /** Binds an Iris target and unlocks Iris' protected depth/color state. */
    public static void bindOutput(Target target) {
        RenderSystem.assertOnRenderThread();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.framebufferId());
        unlockDepthColor();
    }

    /**
     * Blits an effect color texture back to Iris using the existing LDLib2 blit
     * shader. The Iris target is only touched for this final
     * copy, after all Veil rendering has completed on our own FBO.
     */
    public static boolean blitTextureTo(Target target, int textureId) {
        if (target == null || textureId <= 0) return false;

        RenderSystem.assertOnRenderThread();
        ShaderInstance blitShader = LDLibShaders.getBlitShader();
        if (blitShader == null) {
            if (!warnedMissingBlitShader) {
                warnedMissingBlitShader = true;
                Gyromancy.LOGGER.warn("[Gyromancy] LDLib2 blit shader is unavailable; Iris output cannot be restored");
            }
            return false;
        }

        AdvancedFbo.getMainFramebuffer().bind(true);
        GlStateManager._disableDepthTest();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.framebufferId());
        try {
            blitShader.setSampler("DiffuseSampler", textureId);
            blitShader.apply();
            unlockDepthColor();
            GlStateManager._depthMask(false);
            GlStateManager._colorMask(true, true, true, true);

            Tesselator tesselator = RenderSystem.renderThreadTesselator();
            BufferBuilder builder = tesselator.begin(VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.POSITION);
            builder.addVertex(-1, 1, 0);
            builder.addVertex(-1, -1, 0);
            builder.addVertex(1, -1, 0);
            builder.addVertex(1, 1, 0);
            BufferUploader.draw(builder.buildOrThrow());
            return true;
        } finally {
            blitShader.clear();
            GlStateManager._depthMask(true);
            GlStateManager._enableDepthTest();
            restoreMainFramebuffer();
        }
    }

    /** Restores the Veil/Minecraft main framebuffer after a custom pass. */
    public static void restoreMainFramebuffer() {
        AdvancedFbo.getMainFramebuffer().bind(true);
    }

    private static void unlockDepthColor() {
        try {
            Class<?> storage = Class.forName(
                    IRIS_DEPTH_COLOR_STORAGE, false,
                    IrisRenderBridge.class.getClassLoader());
            storage.getMethod("unlockDepthColor").invoke(null);
        } catch (ReflectiveOperationException | LinkageError exception) {
            if (!warnedDepthUnlockFailure) {
                warnedDepthUnlockFailure = true;
                Gyromancy.LOGGER.warn(
                        "[Gyromancy] Could not unlock Iris depth/color state before a custom pass",
                        exception);
            }
        }
    }

    private static boolean isIrisLoaded() {
        if (irisLoaded != null) return irisLoaded;
        try {
            Class.forName(IRIS_CLASS, false, IrisRenderBridge.class.getClassLoader());
            irisLoaded = true;
        } catch (ClassNotFoundException exception) {
            irisLoaded = false;
        }
        return irisLoaded;
    }

    private static Object readField(Class<?> owner, Object instance, String name)
            throws ReflectiveOperationException {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static boolean readBooleanField(Object instance, String name)
            throws ReflectiveOperationException {
        var field = instance.getClass().getField(name);
        return field.getBoolean(instance);
    }

    private static int invokeInt(Object instance, String method)
            throws ReflectiveOperationException {
        return ((Number) instance.getClass().getMethod(method).invoke(instance)).intValue();
    }

    private static boolean invokeBoolean(Object instance, String method)
            throws ReflectiveOperationException {
        return (Boolean) instance.getClass().getMethod(method).invoke(instance);
    }

    private static boolean hasDepthAttachment(int framebufferId) {
        int previousFramebuffer = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebufferId);
            int depthType = GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            if (depthType != GL30.GL_NONE) return true;
            return GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) != GL30.GL_NONE;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousFramebuffer);
        }
    }

    private static boolean isComplete(int framebufferId) {
        int previousFramebuffer = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebufferId);
            return GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)
                    == GL30.GL_FRAMEBUFFER_COMPLETE;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousFramebuffer);
        }
    }

    public record Target(int framebufferId, int width, int height,
                         boolean hasDepthAttachment) {}
}
