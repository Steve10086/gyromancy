package com.astune.gyromancy.client.compat.iris;

import com.astune.gyromancy.Gyromancy;
import com.lowdragmc.lowdraglib2.client.shader.HDRTarget;
import com.lowdragmc.lowdraglib2.client.shader.LDLibShaders;
import com.lowdragmc.photon.Photon;
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Keeps Gyromancy's Veil passes on the framebuffer currently used by Photon/Iris.
 *
 * <p>Photon already has the required Iris mixin. This class deliberately reaches
 * that accessor through reflection so Iris remains an optional runtime mod: the
 * accessor class is never loaded when Photon reports that Iris/Oculus is absent.</p>
 */
public final class PhotonIrisRenderBridge {
    private static final String PHOTON_IRIS_ACCESSOR =
            "com.lowdragmc.photon.core.mixins.iris.ExtendedShaderAccessor";
    private static final String IRIS_DEPTH_COLOR_STORAGE =
            "net.irisshaders.iris.gl.blending.DepthColorStorage";

    private static boolean warnedAccessorFailure;
    private static boolean warnedDepthUnlockFailure;
    private static boolean warnedMissingBlitShader;
    private static boolean warnedMissingParticleShader;
    private static boolean warnedWrongParticleShader;
    private static boolean loggedTarget;

    private PhotonIrisRenderBridge() {}

    /**
     * Finds the same before/after-translucent target selected by Photon.
     * Returns {@code null} for a normal Minecraft/Veil render path.
     */
    @Nullable
    public static Target currentTarget() {
        if (!Photon.isShaderModInstalled()) return null;

        ShaderInstance particleShader = GameRenderer.getParticleShader();
        if (particleShader == null) {
            if (!warnedMissingParticleShader) {
                warnedMissingParticleShader = true;
                Gyromancy.LOGGER.warn(
                        "[Gyromancy] Iris is installed but GameRenderer has no particle shader yet");
            }
            return null;
        }

        try {
            Class<?> accessor = Class.forName(
                    PHOTON_IRIS_ACCESSOR, false,
                    PhotonIrisRenderBridge.class.getClassLoader());
            if (!accessor.isInstance(particleShader)) {
                if (!warnedWrongParticleShader) {
                    warnedWrongParticleShader = true;
                    Gyromancy.LOGGER.warn(
                            "[Gyromancy] Photon Iris accessor is present, but the active particle shader is {}",
                            particleShader.getClass().getName());
                }
                return null;
            }

            Object pipeline = invoke(accessor, particleShader, "getParent");
            boolean beforeTranslucent = pipelineFlag(pipeline, "isBeforeTranslucent");
            String targetMethod = beforeTranslucent
                    ? "getWritingToBeforeTranslucent"
                    : "getWritingToAfterTranslucent";
            Object framebuffer = invoke(accessor, particleShader, targetMethod);
            if (framebuffer == null) return null;
            int framebufferId = ((Number) invoke(framebuffer, "getId")).intValue();
            boolean hasDepth = (Boolean) invoke(framebuffer, "hasDepthAttachment");
            if (framebufferId <= 0) return null;

            Minecraft minecraft = Minecraft.getInstance();
            Target target = new Target(framebufferId, minecraft.getMainRenderTarget().width,
                    minecraft.getMainRenderTarget().height, hasDepth);
            if (!loggedTarget) {
                loggedTarget = true;
                Gyromancy.LOGGER.info(
                        "[Gyromancy] Photon/Iris target active: shader={}, fbo={}, depth={}, beforeTranslucent={}",
                        particleShader.getClass().getName(), framebufferId, hasDepth,
                        beforeTranslucent);
            }
            return target;
        } catch (ReflectiveOperationException | LinkageError exception) {
            if (!warnedAccessorFailure) {
                warnedAccessorFailure = true;
                Gyromancy.LOGGER.warn(
                        "[Gyromancy] Photon Iris framebuffer accessor is unavailable; "
                                + "custom Veil passes will use their normal target", exception);
            }
            return null;
        }
    }

    /**
     * Copies the selected Iris color/depth attachments into an effect FBO.
     *
     * <p>Photon renders into its own HDR target for the same reason: Iris owns
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
     * <p>This is the important part of Photon's Iris path. Iris owns the depth
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
     * Blits an effect color texture back to Iris using the same LDLib2 blit
     * shader used by Photon. The Iris target is only touched for this final
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

        // Photon relies on the main target being bound before touching the
        // extended Iris shader target.
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
                    PhotonIrisRenderBridge.class.getClassLoader());
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

    private static Object invoke(Class<?> owner, Object instance, String method)
            throws ReflectiveOperationException {
        return owner.getMethod(method).invoke(instance);
    }

    private static Object invoke(Object instance, String method)
            throws ReflectiveOperationException {
        return instance.getClass().getMethod(method).invoke(instance);
    }

    private static boolean pipelineFlag(Object pipeline, String fieldName)
            throws ReflectiveOperationException {
        Field field = pipeline.getClass().getField(fieldName);
        return field.getBoolean(pipeline);
    }

    public record Target(int framebufferId, int width, int height,
                         boolean hasDepthAttachment) {}
}
