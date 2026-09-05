package com.astune.gyromancy.client.effect;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.mixin.PhotonRuntimeFilterMaskStateAccessor;
import com.lowdragmc.lowdraglib2.client.shader.HDRTarget;
import com.lowdragmc.lowdraglib2.client.shader.LDLibShaders;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.BlendMode;
import com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.PhotonFXRenderPass;
import com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.PremultipliedBlendPlan;
import com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline;
import com.lowdragmc.photon.client.gameobject.particle.IParticle;
import com.lowdragmc.photon.client.postfx.graph.TargetFormat;
import com.lowdragmc.photon.client.postfx.runtime.CompiledEffect;
import com.lowdragmc.photon.client.postfx.runtime.FormatTarget;
import com.lowdragmc.photon.client.postfx.runtime.MaskGroups;
import com.lowdragmc.photon.client.postfx.runtime.PostEffectStack;
import com.lowdragmc.photon.client.postfx.runtime.PostFXTargetPool;
import com.lowdragmc.photon.client.postfx.runtime.RenderGraphExecutor;
import com.lowdragmc.photon.client.postfx.runtime.SceneBlit;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime bridge for Photon fullscreen graphs which explicitly sample {@value #FX_COLOR_TEXTURE}.
 *
 * <p>The bridge deliberately owns no Photon queue, particle sorting, mask replay, or render-pass
 * lifecycle. Photon still decides which pass is next and still renders that pass exactly once. For a
 * marked pass we only redirect its colour attachment to a transparent HDR layer, run the already
 * configured graph with that layer exposed as {@code FxColor}, then composite the result immediately
 * back into Photon's current target before the next original pass starts.</p>
 *
 * <p>Authors keep using Photon&apos;s existing <em>Post Process</em> and <em>Custom Mask Group</em>
 * settings. A request is routed only when its graph both declares {@code MaskFilter} and samples this
 * texture path. This keeps ordinary Photon post effects and unmarked renderers untouched.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class PhotonRuntimeFilterLayer {

    /** Dynamic Texture Input path selected by the existing fullscreen graph as {@code FxColor}. */
    public static final ResourceLocation FX_COLOR_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "fx_color");

    private static final Map<String, FilterRequest> REQUESTS_BY_GROUP = new LinkedHashMap<>();

    @Nullable private static HDRTarget fxColorTarget;
    @Nullable private static HDRTarget transparentSceneTarget;
    @Nullable private static FormatTarget constantMaskTarget;
    @Nullable private static FormatTarget customDepthTarget;
    @Nullable private static FxColorTexture registeredTexture;
    @Nullable private static Capture activeCapture;
    @Nullable private static Segment openSegment;

    private PhotonRuntimeFilterLayer() {}

    /** Registers the dynamic Texture Input facade. Safe to call on every render frame. */
    public static void onRenderFramePre(RenderFrameEvent.Pre event) {
        registerTextureInput();
        REQUESTS_BY_GROUP.clear();
        openSegment = null;
        activeCapture = null;
    }

    /** Releases only targets owned by this bridge. Photon&apos;s own target pool remains Photon&apos;s. */
    public static void onClientLevelUnload() {
        REQUESTS_BY_GROUP.clear();
        openSegment = null;
        activeCapture = null;
        destroy(fxColorTarget);
        destroy(transparentSceneTarget);
        destroy(constantMaskTarget);
        destroy(customDepthTarget);
        fxColorTarget = null;
        transparentSceneTarget = null;
        constantMaskTarget = null;
        customDepthTarget = null;
    }

    /**
     * Receives a normal Photon post-process submission. Returning {@code true} tells the tiny mixin
     * to remove it from Photon&apos;s frame-end stack because this layer will execute it at the original
     * render-order boundary instead.
     */
    public static boolean registerPostEffect(@Nullable IResourcePath path, Map<String, Object> params,
                                             float weight) {
        if (path == null || weight <= 0f) return false;
        Object configuredGroup = params.get(CompiledEffect.MASK_FILTER_PARAM);
        if (!(configuredGroup instanceof String group) || group.isBlank()) return false;

        CompiledEffect effect = PostEffectStack.resolveEffect(path);
        if (effect == null || !effect.declaresMaskFilter() || !usesFxColor(effect)) return false;

        // One Photon Post Process setting defines one filter for one group. A timeline may submit it
        // repeatedly while it blends; retain the strongest current request, matching the useful part
        // of Photon&apos;s normal request coalescing without recreating its global post-process stack.
        var incoming = new FilterRequest(effect, Map.copyOf(params), Math.min(1f, weight));
        REQUESTS_BY_GROUP.merge(group, incoming,
                (previous, next) -> next.weight() >= previous.weight() ? next : previous);
        return true;
    }

    /**
     * Called from a redirect around PhotonFXRenderPass#drawParticles. The original method remains
     * responsible for building meshes, sorting vertices, choosing CPU/instanced rendering, and
     * material lifecycle.
     */
    public static boolean drawParticles(PhotonFXRenderPass pass, RenderPassPipeline pipeline,
                                        java.util.Collection<IParticle> particles,
                                        net.minecraft.client.Camera camera, float partialTicks) {
        CaptureRoute route = routeFor(pass, pipeline);
        if (route == null) {
            flushOpenSegment(pipeline);
            return pass.drawParticles(pipeline, particles, camera, partialTicks);
        }

        if (openSegment != null && !openSegment.key().equals(route.key())) {
            flushOpenSegment(pipeline);
        }
        if (openSegment == null) {
            openSegment = beginSegment(route, pipeline);
        }
        if (openSegment == null) {
            return pass.drawParticles(pipeline, particles, camera, partialTicks);
        }

        beginCapture(openSegment, pipeline);
        boolean drew = false;
        try {
            drew = pass.drawParticles(pipeline, particles, camera, partialTicks);
            openSegment.markDraw(pass, particles, camera, partialTicks, drew);
            return drew;
        } finally {
            endCapture();
        }
    }

    /** Flushes a segment at RenderPassPipeline#renderQueuedPasses return. */
    public static void flushOpenSegment(RenderPassPipeline pipeline) {
        Segment segment = openSegment;
        if (segment == null) return;
        openSegment = null;
        if (!segment.drewAnything()) return;

        HDRTarget chain = RenderPassPipeline.getDRAW_TARGET();
        HDRTarget fxColor = fxColorTarget;
        if (chain == null || fxColor == null) return;

        int viewportX = GlStateManager.Viewport.x();
        int viewportY = GlStateManager.Viewport.y();
        int viewportWidth = GlStateManager.Viewport.width();
        int viewportHeight = GlStateManager.Viewport.height();
        HDRTarget output = null;
        try {
            HDRTarget transparentScene = ensureTransparentScene(chain.width, chain.height);
            FormatTarget constantMask = ensureConstantMask(chain.width, chain.height);
            clearColorOnly(transparentScene, 0f, 0f, 0f, 0f);

            int maskId = MaskGroups.idOf(segment.route().group());
            float encodedMaskId = maskId / 255f;
            clearColorOnly(constantMask, encodedMaskId, 0f, 0f, 0f);

            Map<String, Object> params = new HashMap<>(segment.route().request().params());
            params.put(CompiledEffect.MASK_FILTER_PARAM, (float) maskId);
            int customDepthTexture = buildCustomDepth(segment, pipeline, chain);

            PostEffectStack.setPostRenderState();
            output = RenderGraphExecutor.execute(segment.route().request().effect(),
                    segment.route().request().weight(), params, transparentScene,
                    chain.getDepthTextureId(), constantMask.getColorTextureId(), customDepthTexture);
            if (output == null) return;

            if (segment.route().composite() == Composite.MAX) {
                compositeMax(output, chain);
            } else {
                chain.bindWrite(true);
                // A runtime filter graph is an isolated premultiplied layer: its own alpha is its
                // coverage. Using the unfiltered FxColor alpha here would turn a transparent sample
                // at PixelUV into black coverage at screenUV, which is exactly the black translucent
                // silhouette the graph is meant to replace.
                SceneBlit.compositePremultipliedToBound(output.getColorTextureId(),
                        output.getColorTextureId(), segment.writeAlpha());
            }
            // A later Photon material which samples the scene must not observe the pre-filtered copy.
            pipeline.markSceneSamplerDirty();
        } finally {
            if (output != null) PostFXTargetPool.release(output);
            PostEffectStack.restorePostRenderState();
            chain.bindWrite(false);
            RenderSystem.viewport(viewportX, viewportY, viewportWidth, viewportHeight);
        }
    }

    /** Queried by the focused isPremultipliedAccumulation mixin while the redirected draw is active. */
    public static boolean usesPremultipliedCaptureBlend() {
        return activeCapture != null && activeCapture.route().composite() == Composite.OVER;
    }

    /** Rebinds the isolated target after a Photon material resolves a scene sampler and rebinds DRAW_TARGET. */
    public static void bindCaptureTargetForDraw() {
        Capture capture = activeCapture;
        if (capture == null || fxColorTarget == null) return;
        fxColorTarget.bindWrite(false);
        RenderSystem.viewport(capture.viewportX(), capture.viewportY(),
                capture.viewportWidth(), capture.viewportHeight());
        // MAX ignores alpha. Keeping its isolated alpha at zero prevents the fullscreen graph from
        // treating a glow/MAX mesh as opaque coverage when it is later max-composited.
        boolean writeAlpha = capture.route().composite() != Composite.MAX;
        GlStateManager._colorMask(true, true, true, writeAlpha);
    }

    /** Texture-manager facade lookup used by the graph&apos;s ordinary Texture Input node. */
    public static int fxColorTextureId() {
        if (fxColorTarget != null) return fxColorTarget.getColorTextureId();
        var main = Minecraft.getInstance().getMainRenderTarget();
        return ensureFxColor(main.width, main.height).getColorTextureId();
    }

    private static void registerTextureInput() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getTextureManager() == null || registeredTexture != null) return;
        registeredTexture = new FxColorTexture();
        minecraft.getTextureManager().register(FX_COLOR_TEXTURE, registeredTexture);
        Gyromancy.LOGGER.info("[Gyromancy] Photon runtime filter layer registered FxColor as {}", FX_COLOR_TEXTURE);
    }

    @Nullable
    private static CaptureRoute routeFor(PhotonFXRenderPass pass, RenderPassPipeline pipeline) {
        if (pipeline.isMaskSubPass() || pipeline.isWireframeSubPass()) return null;
        RendererSetting.Runtime renderer = pass.renderer;
        if (!renderer.isWriteCustomMask()) return null;
        FilterRequest request = REQUESTS_BY_GROUP.get(renderer.getMaskGroup());
        if (request == null) return null;

        Composite composite = chooseComposite(renderer, pipeline);
        if (composite == null) return null;
        return new CaptureRoute(renderer.getMaskGroup(), request, composite);
    }

    @Nullable
    private static Composite chooseComposite(RendererSetting.Runtime renderer, RenderPassPipeline pipeline) {
        var materials = renderer.getMaterials();
        if (materials.isEmpty()) return null;
        // MAX can be restored exactly on Photon&apos;s ordinary in-place target using GL_MAX. Photon itself
        // intentionally approximates it in transparent/Iris accumulators, so keep its existing path there.
        boolean allMax = !pipeline.isPremultipliedAccumulation() && materials.stream().allMatch(material -> {
            BlendMode blend = material.getBlendMode();
            return blend.isEnableBlend() && blend.getBlendFunc() == BlendMode.BlendFuc.MAX;
        });
        if (allMax) return Composite.MAX;
        return PremultipliedBlendPlan.areLayerSafe(materials) ? Composite.OVER : null;
    }

    @Nullable
    private static Segment beginSegment(CaptureRoute route, RenderPassPipeline pipeline) {
        HDRTarget chain = RenderPassPipeline.getDRAW_TARGET();
        if (chain == null) return null;
        HDRTarget fxColor = ensureFxColor(chain.width, chain.height);
        // FxDepth in the existing graph is wired from Photon CustomDepth, not SceneDepth. Give the
        // isolated layer its OWN depth texture: start with a copy of the current scene depth so the
        // original world occlusion remains valid, then let this captured renderer write its normal
        // material depth into that copy. Sharing DRAW_TARGET's attachment here makes FxDepth describe
        // the world/background instead of this filtered segment (and can mutate the real scene depth).
        fxColor.copyDepthFrom(chain);
        clearColorOnly(fxColor, 0f, 0f, 0f, 0f);
        return new Segment(route, pipeline.isPremultipliedAccumulation());
    }

    private static void beginCapture(Segment segment, RenderPassPipeline pipeline) {
        HDRTarget chain = RenderPassPipeline.getDRAW_TARGET();
        if (chain == null) return;
        activeCapture = new Capture(segment.route(), GlStateManager.Viewport.x(), GlStateManager.Viewport.y(),
                GlStateManager.Viewport.width(), GlStateManager.Viewport.height());
        bindCaptureTargetForDraw();
    }

    private static void endCapture() {
        activeCapture = null;
        GlStateManager._colorMask(true, true, true, true);
    }

    private static HDRTarget ensureFxColor(int width, int height) {
        fxColorTarget = ensureHdrTarget(fxColorTarget, width, height, GL11.GL_NEAREST);
        return fxColorTarget;
    }

    private static HDRTarget ensureTransparentScene(int width, int height) {
        transparentSceneTarget = ensureHdrTarget(transparentSceneTarget, width, height, GL11.GL_NEAREST);
        return transparentSceneTarget;
    }

    private static HDRTarget ensureHdrTarget(@Nullable HDRTarget current, int width, int height, int filter) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        if (current != null && current.width == safeWidth && current.height == safeHeight) return current;
        destroy(current);
        HDRTarget target = new HDRTarget(safeWidth, safeHeight, filter, true);
        target.setClearColor(0f, 0f, 0f, 0f);
        return target;
    }

    private static FormatTarget ensureConstantMask(int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        if (constantMaskTarget == null) {
            constantMaskTarget = new FormatTarget(safeWidth, safeHeight, GL11.GL_NEAREST, TargetFormat.R8);
        } else if (constantMaskTarget.width != safeWidth || constantMaskTarget.height != safeHeight) {
            constantMaskTarget.resize(safeWidth, safeHeight, Minecraft.ON_OSX);
        }
        return constantMaskTarget;
    }

    /**
     * Rebuilds Photon CustomDepth for this isolated segment with Photon&apos;s own mask material. Normal
     * translucent materials commonly have depthMask disabled; their colour draw therefore cannot be
     * used as an effect-surface depth source. Photon&apos;s native CustomMask pass solves that by drawing
     * the same geometry into a private target with MASK_MATERIAL (depth write forced on). Reusing that
     * exact path keeps depth independent of the renderer&apos;s normal depth-mask setting.
     */
    private static int buildCustomDepth(Segment segment, RenderPassPipeline pipeline, HDRTarget chain) {
        if (!usesCustomDepth(segment.route().request().effect())) return chain.getDepthTextureId();
        FormatTarget target = ensureCustomDepthTarget(chain.width, chain.height);
        // Keep terrain/opaque occlusion, then let Photon&apos;s mask material write the FX surfaces.
        target.copyDepthFrom(chain);
        clearColorOnly(target, 0f, 0f, 0f, 0f);

        int viewportX = GlStateManager.Viewport.x();
        int viewportY = GlStateManager.Viewport.y();
        int viewportWidth = GlStateManager.Viewport.width();
        int viewportHeight = GlStateManager.Viewport.height();
        var maskState = (PhotonRuntimeFilterMaskStateAccessor) pipeline;
        try {
            target.bindWrite(false);
            RenderSystem.viewport(viewportX, viewportY, viewportWidth, viewportHeight);
            maskState.gyromancy$setMaskSubPass(true);
            for (CapturedBatch batch : segment.batches()) {
                batch.pass().prepareStatus(pipeline);
                try {
                    // PhotonFXRenderPass&apos;s own mask branch selects MASK_MATERIAL, preserves the
                    // configured alpha cutoff, and forces depth writes into this private target.
                    batch.pass().drawParticles(pipeline, batch.particles(), batch.camera(), batch.partialTicks());
                } finally {
                    batch.pass().releaseStatus(pipeline);
                }
            }
        } finally {
            maskState.gyromancy$setMaskSubPass(false);
            chain.bindWrite(false);
            RenderSystem.viewport(viewportX, viewportY, viewportWidth, viewportHeight);
        }
        return target.getDepthTextureId();
    }

    private static FormatTarget ensureCustomDepthTarget(int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        if (customDepthTarget == null) {
            customDepthTarget = new FormatTarget(safeWidth, safeHeight, GL11.GL_NEAREST, TargetFormat.R8, true);
        } else if (customDepthTarget.width != safeWidth || customDepthTarget.height != safeHeight) {
            customDepthTarget.resize(safeWidth, safeHeight, Minecraft.ON_OSX);
        }
        return customDepthTarget;
    }

    private static void clearColorOnly(HDRTarget target, float red, float green, float blue, float alpha) {
        target.bindWrite(false);
        GlStateManager._colorMask(true, true, true, true);
        GlStateManager._clearColor(red, green, blue, alpha);
        GlStateManager._clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
    }

    private static void compositeMax(HDRTarget output, HDRTarget chain) {
        chain.bindWrite(true);
        ShaderInstance shader = LDLibShaders.getBlitShader();
        shader.setSampler("DiffuseSampler", output.getColorTextureId());
        shader.apply();
        GlStateManager._enableBlend();
        GlStateManager._blendEquation(GL14.GL_MAX);
        GlStateManager._blendFunc(GL11.GL_ONE, GL11.GL_ONE);
        GlStateManager._colorMask(true, true, true, false);
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        SceneBlit.drawFullscreenQuad();
        shader.clear();
        GlStateManager._blendEquation(GL14.GL_FUNC_ADD);
        GlStateManager._colorMask(true, true, true, true);
        GlStateManager._depthMask(true);
        GlStateManager._enableDepthTest();
        RenderSystem.defaultBlendFunc();
    }

    private static boolean usesFxColor(CompiledEffect effect) {
        return effect.passes().stream()
                .flatMap(pass -> pass.textures().values().stream())
                .anyMatch(ref -> ref.source() == CompiledEffect.ResourceRef.Source.ASSET
                        && ref.asset() != null && FX_COLOR_TEXTURE.toString().equals(ref.asset().location()));
    }

    private static boolean usesCustomDepth(CompiledEffect effect) {
        return effect.passes().stream()
                .flatMap(pass -> pass.textures().values().stream())
                .anyMatch(ref -> ref.source() == CompiledEffect.ResourceRef.Source.CUSTOM_DEPTH);
    }

    private static void destroy(@Nullable HDRTarget target) {
        if (target != null) target.destroyBuffers();
    }

    private enum Composite { OVER, MAX }

    private record FilterRequest(CompiledEffect effect, Map<String, Object> params, float weight) {}

    private record CaptureRoute(String group, FilterRequest request, Composite composite) {
        private String key() {
            // The graph and its parameters are fixed for a group during a frame; the composite mode
            // is additionally part of the boundary because MAX and alpha-over cannot share a layer.
            return group + '\u0000' + composite;
        }
    }

    private static final class Segment {
        private final CaptureRoute route;
        private final boolean writeAlpha;
        private final List<CapturedBatch> batches = new ArrayList<>();
        private boolean drewAnything;

        private Segment(CaptureRoute route, boolean writeAlpha) {
            this.route = route;
            this.writeAlpha = writeAlpha;
        }

        private CaptureRoute route() { return route; }
        private boolean writeAlpha() { return writeAlpha; }
        private boolean drewAnything() { return drewAnything; }
        private List<CapturedBatch> batches() { return batches; }
        private String key() { return route.key(); }
        private void markDraw(PhotonFXRenderPass pass, java.util.Collection<IParticle> particles,
                              net.minecraft.client.Camera camera, float partialTicks, boolean drew) {
            if (!drew) return;
            drewAnything = true;
            batches.add(new CapturedBatch(pass, particles, camera, partialTicks));
        }
    }

    private record Capture(CaptureRoute route, int viewportX, int viewportY,
                           int viewportWidth, int viewportHeight) {}

    private record CapturedBatch(PhotonFXRenderPass pass, java.util.Collection<IParticle> particles,
                                 net.minecraft.client.Camera camera, float partialTicks) {}

    /** TextureManager owns this facade; its GL id always follows the active HDR attachment. */
    private static final class FxColorTexture extends AbstractTexture {
        @Override
        public void load(ResourceManager resourceManager) {
            // This texture is framebuffer-backed, not loaded from a resource pack.
        }

        @Override
        public int getId() {
            return fxColorTextureId();
        }

        @Override
        public void close() {
            // The HDR target owns and frees its attachment explicitly.
        }
    }
}
