package com.astune.gyromancy.client.effect;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.geometry.SurfaceFrame;
import com.astune.gyromancy.entity.projection.ProjectionCanvasEntity;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.framebuffer.FramebufferAttachmentDefinition;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.vertex.VertexArray;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.*;
import java.util.function.IntUnaryOperator;

public final class ClientRayEffects {

    private ClientRayEffects() {}

    public static final double DEFAULT_BEAM_HEIGHT = 1.5;
    public static final int DEFAULT_FADE_IN_TICKS = 0;

    private static final double PLANE_CLEARANCE = 0.01;
    private static final double SOURCE_HALF_SIZE = 0.5;
    private static final double PROJECTION_PADDING = 0.08;
    private static final double MIN_RAY_NORMAL_DOT = 0.05;
    private static final ResourceLocation RAY_MESH_RENDER_TYPE =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "ray_projection_mesh");
    private static final ResourceLocation RAY_MESH_SHADER =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "ray_projection_mesh");
    private static final ResourceLocation RAY_COMPOSITE_FRAMEBUFFER =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "ray_composite");
    private static final ResourceLocation RAY_COMPOSITE_SHADER =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "ray_composite");
    private static final Map<EffectKey, Effect> effects = new LinkedHashMap<>();
    private static final MeshVertex[] NO_MESH_VERTICES = new MeshVertex[0];
    private static MeshBeamRenderer meshRenderer;
    private static AdvancedFbo rayCompositeFbo;
    private static int rayCompositeWidth = -1;
    private static int rayCompositeHeight = -1;
    private static boolean warnedMissingMeshShader;
    private static boolean warnedMissingCompositeShader;

    public static void clearAll() {
        effects.clear();
    }

    public static void spawnOrRefresh(Vec3 center, Direction face, Vec3 worldRayDir,
                                       Vec3 sourceU, Vec3 sourceV,
                                       int[] symbolLayer,
                                       int sourceWidth, int sourceHeight,
                                       IntUnaryOperator colorBySymbolValue,
                                       int color, int lifetime, double beamHeight) {
        spawnOrRefresh(center, face, worldRayDir, sourceU, sourceV, null, symbolLayer,
                sourceWidth, sourceHeight, colorBySymbolValue, color, lifetime, beamHeight,
                DEFAULT_FADE_IN_TICKS);
    }

    public static void spawnOrRefresh(Vec3 center, Direction face, Vec3 worldRayDir,
                                       Vec3 sourceU, Vec3 sourceV,
                                       ResourceLocation maskTexture, int[] symbolLayer,
                                       int sourceWidth, int sourceHeight,
                                       IntUnaryOperator colorBySymbolValue,
                                       int color, int lifetime) {
        spawnOrRefresh(center, face, worldRayDir, sourceU, sourceV, maskTexture, symbolLayer,
                sourceWidth, sourceHeight, colorBySymbolValue, color, lifetime, DEFAULT_BEAM_HEIGHT,
                DEFAULT_FADE_IN_TICKS);
    }

    public static void spawnOrRefresh(Vec3 center, Direction face, Vec3 worldRayDir,
                                       Vec3 sourceU, Vec3 sourceV,
                                       ResourceLocation maskTexture, int[] symbolLayer,
                                       int sourceWidth, int sourceHeight,
                                       IntUnaryOperator colorBySymbolValue,
                                       int color, int lifetime, double beamHeight) {
        spawnOrRefresh(center, face, worldRayDir, sourceU, sourceV, maskTexture, symbolLayer,
                sourceWidth, sourceHeight, colorBySymbolValue, color, lifetime, beamHeight,
                DEFAULT_FADE_IN_TICKS);
    }

    public static void spawnOrRefresh(Vec3 center, Direction face, Vec3 worldRayDir,
                                       Vec3 sourceU, Vec3 sourceV,
                                       ResourceLocation maskTexture, int[] symbolLayer,
                                       int sourceWidth, int sourceHeight,
                                       IntUnaryOperator colorBySymbolValue,
                                       int color, int lifetime, double beamHeight, int fadeInTicks) {
        spawnOrRefresh(EffectKey.forGeometry(center, face),
                center, Vec3.atLowerCornerOf(face.getNormal()), worldRayDir,
                sourceU, sourceV, maskTexture,
                symbolLayer, sourceWidth, sourceHeight, colorBySymbolValue,
                color, lifetime, beamHeight, fadeInTicks);
    }

    public static void spawnForLifecycle(
            UUID lifecycleId,
            int partIndex,
            Vec3 center,
            Direction face,
            Vec3 worldRayDir,
            Vec3 sourceU,
            Vec3 sourceV,
            int[] symbolLayer,
            int sourceWidth,
            int sourceHeight,
            IntUnaryOperator colorBySymbolValue,
            int color,
            int lifetime,
            double beamHeight
    ) {
        spawnForLifecycle(lifecycleId, partIndex, center,
                Vec3.atLowerCornerOf(face.getNormal()), worldRayDir,
                sourceU, sourceV, symbolLayer, sourceWidth, sourceHeight,
                colorBySymbolValue, color, lifetime, beamHeight);
    }

    /** Core lifecycle API for effects emitted from an arbitrarily oriented surface. */
    public static void spawnForLifecycle(
            UUID lifecycleId,
            int partIndex,
            Vec3 center,
            Vec3 sourceNormal,
            Vec3 worldRayDir,
            Vec3 sourceU,
            Vec3 sourceV,
            int[] symbolLayer,
            int sourceWidth,
            int sourceHeight,
            IntUnaryOperator colorBySymbolValue,
            int color,
            int lifetime,
            double beamHeight
    ) {
        spawnOrRefresh(EffectKey.forLifecycle(lifecycleId, partIndex),
                center, sourceNormal, worldRayDir, sourceU, sourceV, null,
                symbolLayer, sourceWidth, sourceHeight, colorBySymbolValue,
                color, lifetime, beamHeight, DEFAULT_FADE_IN_TICKS);
    }

    /** Compatibility overload for non-Pigmentum binary masks used by array sync packets. */
    public static void spawnForLifecycle(
            UUID lifecycleId,
            int partIndex,
            Vec3 center,
            Direction face,
            Vec3 worldRayDir,
            Vec3 sourceU,
            Vec3 sourceV,
            byte[] mask,
            int sourceWidth,
            int sourceHeight,
            IntUnaryOperator colorBySymbolValue,
            int color,
            int lifetime,
            double beamHeight
    ) {
        spawnForLifecycle(lifecycleId, partIndex, center,
                Vec3.atLowerCornerOf(face.getNormal()), worldRayDir,
                sourceU, sourceV, mask, sourceWidth, sourceHeight,
                colorBySymbolValue, color, lifetime, beamHeight);
    }

    /** Free-surface overload for binary masks used by array sync packets. */
    public static void spawnForLifecycle(
            UUID lifecycleId,
            int partIndex,
            Vec3 center,
            Vec3 sourceNormal,
            Vec3 worldRayDir,
            Vec3 sourceU,
            Vec3 sourceV,
            byte[] mask,
            int sourceWidth,
            int sourceHeight,
            IntUnaryOperator colorBySymbolValue,
            int color,
            int lifetime,
            double beamHeight
    ) {
        int[] widenedMask = new int[mask.length];
        for (int i = 0; i < mask.length; i++) widenedMask[i] = mask[i] & 0xFF;
        spawnForLifecycle(lifecycleId, partIndex, center, sourceNormal, worldRayDir,
                sourceU, sourceV,
                widenedMask, sourceWidth, sourceHeight, colorBySymbolValue,
                color, lifetime, beamHeight);
    }

    public static void stopLifecycle(UUID lifecycleId) {
        effects.keySet().removeIf(key -> lifecycleId.equals(key.lifecycleId()));
    }

    static long activeLifecycleEffectCount(UUID lifecycleId) {
        return effects.keySet().stream()
                .filter(key -> lifecycleId.equals(key.lifecycleId()))
                .count();
    }

    static Optional<Vec3> lifecycleSourceNormal(UUID lifecycleId, int partIndex) {
        Effect effect = effects.get(EffectKey.forLifecycle(lifecycleId, partIndex));
        return effect == null ? Optional.empty() : Optional.of(effect.sourceNormal);
    }

    static Optional<Vec3> lifecycleCenter(UUID lifecycleId, int partIndex) {
        Effect effect = effects.get(EffectKey.forLifecycle(lifecycleId, partIndex));
        return effect == null ? Optional.empty() : Optional.of(effect.center);
    }

    static OptionalInt lifecycleSourceEntityId(UUID lifecycleId, int partIndex) {
        Effect effect = effects.get(EffectKey.forLifecycle(lifecycleId, partIndex));
        return effect == null || effect.sourceEntityId < 0
                ? OptionalInt.empty()
                : OptionalInt.of(effect.sourceEntityId);
    }

    static int geometryMeshVertexCount(Vec3 center, Direction face) {
        Effect effect = effects.get(EffectKey.forGeometry(center, face));
        return effect == null ? 0 : effect.meshVertices.length;
    }

    public static void bindLifecycleSource(UUID lifecycleId, int partIndex, int entityId) {
        Effect effect = effects.get(EffectKey.forLifecycle(lifecycleId, partIndex));
        if (effect != null) effect.sourceEntityId = entityId;
    }

    public static void bindLifecycleEndTick(
            UUID lifecycleId, int partIndex, long endTick, int durationTicks) {
        Effect effect = effects.get(EffectKey.forLifecycle(lifecycleId, partIndex));
        if (effect != null && endTick != Long.MIN_VALUE) {
            effect.bindEndTick(endTick, durationTicks);
        }
    }

    private static void spawnOrRefresh(
            EffectKey key,
            Vec3 center,
            Vec3 sourceNormal,
            Vec3 worldRayDir,
            Vec3 sourceU,
            Vec3 sourceV,
            ResourceLocation ignoredMaskTexture,
            int[] symbolLayer,
            int sourceWidth,
            int sourceHeight,
            IntUnaryOperator colorBySymbolValue,
            int color,
            int lifetime,
            double beamHeight,
            int fadeInTicks
    ) {
        Effect existing = effects.get(key);
        if (existing != null) {
            boolean preserveLifetime = key.lifecycleId() != null;
            existing.center = center;
            MeshVertex[] meshVertices = existing.meshVertices;
            if (!Arrays.equals(existing.symbolLayerSnapshot, symbolLayer)
                    || existing.sourceWidth != sourceWidth
                    || existing.sourceHeight != sourceHeight) {
                meshVertices = compactMesh(symbolLayer, sourceWidth, sourceHeight, colorBySymbolValue);
                if (meshVertices.length == 0) {
                    effects.remove(key);
                    return;
                }
                existing.symbolLayerSnapshot = copySymbolLayer(symbolLayer);
            }
            existing.refresh(sourceNormal, worldRayDir, sourceU, sourceV,
                    meshVertices,
                    sourceWidth, sourceHeight, color, lifetime, beamHeight, fadeInTicks,
                    preserveLifetime);
            return;
        }
        MeshVertex[] meshVertices = compactMesh(symbolLayer, sourceWidth, sourceHeight, colorBySymbolValue);
        if (meshVertices.length == 0) return;
        Effect effect = new Effect(center, sourceNormal, worldRayDir,
                sourceU, sourceV, meshVertices,
                sourceWidth, sourceHeight, color, lifetime, beamHeight, fadeInTicks);
        effect.symbolLayerSnapshot = copySymbolLayer(symbolLayer);
        effects.put(key, effect);
    }

    private static int[] copySymbolLayer(int[] symbolLayer) {
        return symbolLayer == null ? null : symbolLayer.clone();
    }

    static MeshVertex[] compactMesh(int[] symbolLayer, int width, int height, IntUnaryOperator colorBySymbolValue) {
        if (symbolLayer == null || width <= 0 || height <= 0 || symbolLayer.length < width * height) return NO_MESH_VERTICES;

        boolean[] filled = new boolean[width * height];
        int[] argbByPixel = new int[width * height];
        int count = 0;
        for (int i = 0; i < width * height; i++) {
            int symbolValue = symbolLayer[i];
            if (symbolValue == 0) continue;
            int argb = colorBySymbolValue.applyAsInt(symbolValue);
            if (((argb >>> 24) & 0xFF) == 0) continue;
            filled[i] = true;
            argbByPixel[i] = argb;
            count++;
        }
        if (count == 0) return NO_MESH_VERTICES;

        ArrayList<MeshVertex> out = new ArrayList<>(count * 24);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (!filled[index]) continue;

                float u0 = (float) x / width - 0.5f;
                float u1 = (float) (x + 1) / width - 0.5f;
                float v0 = 0.5f - (float) y / height;
                float v1 = 0.5f - (float) (y + 1) / height;
                Color color = Color.fromArgb(argbByPixel[index]);

                addMeshQuad(out, color, u0, v1, 0, u1, v1, 0, u1, v0, 0, u0, v0, 0);
                addMeshQuad(out, color, u0, v1, 1, u1, v1, 1, u1, v0, 1, u0, v0, 1);
                if (!isFilled(filled, width, height, x - 1, y)) {
                    addMeshQuad(out, color, u0, v1, 0, u0, v0, 0, u0, v0, 1, u0, v1, 1);
                }
                if (!isFilled(filled, width, height, x + 1, y)) {
                    addMeshQuad(out, color, u1, v0, 0, u1, v1, 0, u1, v1, 1, u1, v0, 1);
                }
                if (!isFilled(filled, width, height, x, y - 1)) {
                    addMeshQuad(out, color, u0, v0, 0, u1, v0, 0, u1, v0, 1, u0, v0, 1);
                }
                if (!isFilled(filled, width, height, x, y + 1)) {
                    addMeshQuad(out, color, u1, v1, 0, u0, v1, 0, u0, v1, 1, u1, v1, 1);
                }
            }
        }
        return out.toArray(NO_MESH_VERTICES);
    }

    private static boolean isFilled(boolean[] filled, int width, int height, int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height && filled[y * width + x];
    }

    private static void addMeshQuad(ArrayList<MeshVertex> out, Color color,
                                    float u0, float v0, float t0,
                                    float u1, float v1, float t1,
                                    float u2, float v2, float t2,
                                    float u3, float v3, float t3) {
        out.add(new MeshVertex(u0, v0, t0, color.r, color.g, color.b, color.a));
        out.add(new MeshVertex(u1, v1, t1, color.r, color.g, color.b, color.a));
        out.add(new MeshVertex(u2, v2, t2, color.r, color.g, color.b, color.a));
        out.add(new MeshVertex(u3, v3, t3, color.r, color.g, color.b, color.a));
    }

    private static long key(Vec3 center, Direction face) {
        long h = 31 * 31 * Double.doubleToLongBits(center.x)
                + 31 * Double.doubleToLongBits(center.y)
                + Double.doubleToLongBits(center.z);
        return h ^ (long) face.ordinal() << 56;
    }

    private record EffectKey(UUID lifecycleId, int partIndex, long geometryKey) {
        private static EffectKey forGeometry(Vec3 center, Direction face) {
            return new EffectKey(null, 0, key(center, face));
        }

        private static EffectKey forLifecycle(UUID lifecycleId, int partIndex) {
            return new EffectKey(Objects.requireNonNull(lifecycleId), partIndex, 0L);
        }
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (effects.isEmpty()) return;

        AdvancedFbo compositeFbo = ensureRayCompositeFbo();
        if (compositeFbo != null) {
            compositeFbo.clear(0f, 0f, 0f, 0f, GL11.GL_COLOR_BUFFER_BIT);
            AdvancedFbo.getMainFramebuffer().resolveToAdvancedFbo(compositeFbo, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        }

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long gameTime = Minecraft.getInstance().level == null
                ? Long.MIN_VALUE
                : Minecraft.getInstance().level.getGameTime();
        boolean needsComposite = false;

        Iterator<Map.Entry<EffectKey, Effect>> it = effects.entrySet().iterator();
        while (it.hasNext()) {
            Effect e = it.next().getValue();
            e.age++;
            e.ticksSinceRefresh++;
            if (e.expired(gameTime)) { it.remove(); continue; }
            needsComposite |= renderOne(e, camPos, partialTick, gameTime);
        }

        if (needsComposite && compositeFbo != null) compositeRayBuffer(compositeFbo);
    }

    private static boolean renderOne(
            Effect e, Vec3 cameraPos, float partialTick, long gameTime) {
        float fade = e.lifecycleEndTick == Long.MIN_VALUE
                ? alphaFade(e.age, e.ticksSinceRefresh, e.lifetime, e.fadeInTicks)
                : deadlineFade(
                        gameTime + partialTick, e.lifecycleEndTick,
                        e.lifecycleDurationTicks, e.fadeInTicks);

        EffectGeometry geometry = renderGeometry(e, partialTick);
        Vec3 normal = geometry.normal();
        Projection projection = projectionPlane(
                geometry.center(), normal, geometry.sourceU(), geometry.sourceV(),
                geometry.worldRayDir(), cameraPos, e.beamHeight);

        int argb = e.color;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b2 = (argb & 0xFF) / 255f;
        float ba = ((argb >> 24) & 0xFF) / 255f;
        float a = ba * fade;

        ShaderProgram meshShader = VeilRenderSystem.renderer().getShaderManager().getShader(RAY_MESH_SHADER);
        if (meshShader == null || !meshShader.isValid() || e.meshVertices.length == 0) {
            if (!warnedMissingMeshShader) {
                warnedMissingMeshShader = true;
                Gyromancy.LOGGER.warn("[Gyromancy] Ray projection mesh shader {} is not loaded", RAY_MESH_SHADER);
            }
            return false;
        }

        if (meshRenderer == null) meshRenderer = new MeshBeamRenderer();
        return meshRenderer.draw(VeilRenderType.get(RAY_MESH_RENDER_TYPE), e.meshVertices, () -> {
            Vec3 sourceCenter = geometry.center().add(normal.scale(PLANE_CLEARANCE));
            meshShader.getUniform("SourceCenter").setVector(
                    (float) sourceCenter.x, (float) sourceCenter.y, (float) sourceCenter.z);
            meshShader.getUniform("SourceU").setVector(
                    (float) projection.sourceU.x, (float) projection.sourceU.y, (float) projection.sourceU.z);
            meshShader.getUniform("SourceV").setVector(
                    (float) projection.sourceV.x, (float) projection.sourceV.y, (float) projection.sourceV.z);
            meshShader.getUniform("BeamWorld").setVector((float) projection.beamWorldX, (float) projection.beamWorldY, (float) projection.beamWorldZ);
            meshShader.getUniform("EffectTint").setVector(r, g, b2, a);
        });
    }

    private static EffectGeometry renderGeometry(Effect effect, float partialTick) {
        if (effect.sourceEntityId >= 0 && Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getEntity(effect.sourceEntityId)
                instanceof ProjectionCanvasEntity projection) {
            SurfaceFrame frame = projection.renderSurfaceFrame(partialTick);
            double entranceScale = projection.renderEntranceScale(partialTick);
            return projectionGeometry(
                    frame,
                    effect.sourceU.length() * entranceScale,
                    effect.sourceV.length() * entranceScale);
        }
        return new EffectGeometry(
                effect.center, effect.sourceNormal.normalize(),
                effect.sourceU, effect.sourceV, effect.worldRayDir);
    }

    static EffectGeometry projectionGeometry(SurfaceFrame frame, double sourceWidth, double sourceHeight) {
        return new EffectGeometry(
                frame.origin(), frame.normal(),
                frame.axisU().scale(sourceWidth),
                frame.axisV().scale(sourceHeight),
                frame.normal());
    }

    record EffectGeometry(Vec3 center, Vec3 normal, Vec3 sourceU,
                          Vec3 sourceV, Vec3 worldRayDir) {}

    static float alphaFade(int age, int ticksSinceRefresh, int lifetime, int fadeInTicks) {
        float fadeOut = Math.max(0f, 1f - (float) ticksSinceRefresh / Math.max(1, lifetime));
        if (fadeInTicks <= 0) return fadeOut;
        return fadeOut * Math.min(1f, (float) age / fadeInTicks);
    }

    static float deadlineFade(
            double gameTime, long endTick, int durationTicks, int fadeInTicks) {
        double duration = Math.max(1, durationTicks);
        double remaining = Math.clamp(endTick - gameTime, 0.0, duration);
        float fadeOut = (float) (remaining / duration);
        if (fadeInTicks <= 0) return fadeOut;
        double elapsed = duration - remaining;
        return fadeOut * (float) Math.min(1.0, elapsed / fadeInTicks);
    }

    private static AdvancedFbo ensureRayCompositeFbo() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (rayCompositeFbo != null
                && rayCompositeWidth == main.width
                && rayCompositeHeight == main.height) {
            return rayCompositeFbo;
        }

        if (rayCompositeFbo != null) rayCompositeFbo.free();
        rayCompositeFbo = AdvancedFbo.withSize(main.width, main.height)
                .setFormat(FramebufferAttachmentDefinition.Format.RGBA16F)
                .addColorTextureBuffer()
                .setDepthTextureBuffer()
                .setDebugLabel("gyromancy:ray_composite")
                .build(true);
        rayCompositeWidth = main.width;
        rayCompositeHeight = main.height;
        VeilRenderSystem.renderer().getFramebufferManager().setFramebuffer(RAY_COMPOSITE_FRAMEBUFFER, rayCompositeFbo);
        return rayCompositeFbo;
    }

    private static void compositeRayBuffer(AdvancedFbo compositeFbo) {
        ShaderProgram shader = VeilRenderSystem.renderer().getShaderManager().getShader(RAY_COMPOSITE_SHADER);
        if (shader == null || !shader.isValid()) {
            if (!warnedMissingCompositeShader) {
                warnedMissingCompositeShader = true;
                Gyromancy.LOGGER.warn("[Gyromancy] Ray composite shader {} is not loaded", RAY_COMPOSITE_SHADER);
            }
            return;
        }

        AdvancedFbo.getMainFramebuffer().bind(true);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
        RenderSystem.blendFunc(GL11.GL_ONE, GL11.GL_ONE);
        shader.bind();
        shader.setFramebufferSamplers(compositeFbo);
        shader.setDefaultUniforms(VertexFormat.Mode.TRIANGLE_STRIP);
        shader.bindSamplers(0);
        VeilRenderSystem.drawScreenQuad();
        shader.clearSamplers();
        ShaderProgram.unbind();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    static Projection projectionPlane(Vec3 center, Vec3 normal, Vec3 worldRayDir, Vec3 cameraPos) {
        Vec3 n = normal.normalize();
        Vec3 sourceU = stableAxisInPlane(n).normalize();
        Vec3 sourceV = n.cross(sourceU).normalize();
        return projectionPlane(center, n, sourceU, sourceV, worldRayDir, cameraPos, DEFAULT_BEAM_HEIGHT);
    }

    static Projection projectionPlane(Vec3 center, Vec3 normal, Vec3 sourceU, Vec3 sourceV,
                                      Vec3 worldRayDir, Vec3 cameraPos) {
        return projectionPlane(center, normal, sourceU, sourceV, worldRayDir, cameraPos, DEFAULT_BEAM_HEIGHT);
    }

    static Projection projectionPlane(Vec3 center, Vec3 normal, Vec3 sourceU, Vec3 sourceV,
                                      Vec3 worldRayDir, Vec3 cameraPos, double beamHeight) {
        Vec3 n = normal.normalize();
        if (sourceU.lengthSqr() < 1e-10 || sourceV.lengthSqr() < 1e-10) {
            sourceU = stableAxisInPlane(n).normalize();
            sourceV = n.cross(sourceU).normalize();
        }
        Vec3 orbit = cameraPos.subtract(center);
        if (orbit.lengthSqr() < 1e-10) orbit = n;
        orbit = orbit.normalize();
        if (orbit.dot(n) < 0.1) orbit = orbit.add(n.scale(0.1 - orbit.dot(n))).normalize();

        Vec3 planeNormal = orbit;
        double sourceNormalExtent = SOURCE_HALF_SIZE
                * (Math.abs(sourceU.dot(planeNormal)) + Math.abs(sourceV.dot(planeNormal)));
        Vec3 planeCenter = center.add(planeNormal.scale(sourceNormalExtent + PLANE_CLEARANCE));
        Vec3 uAxis = sourceU.subtract(planeNormal.scale(sourceU.dot(planeNormal)));
        if (uAxis.lengthSqr() < 1e-10) uAxis = stableAxisInPlane(planeNormal);
        uAxis = uAxis.normalize();
        Vec3 vAxis = planeNormal.cross(uAxis).normalize();

        Vec3 ray = worldRayDir.lengthSqr() < 1e-10 ? n : worldRayDir.normalize();
        if (Math.abs(ray.dot(n)) < MIN_RAY_NORMAL_DOT) ray = ray.add(n.scale(MIN_RAY_NORMAL_DOT)).normalize();

        Vec3 beamWorld = ray.scale(Math.max(0, beamHeight));
        double baseOffsetU = center.subtract(planeCenter).dot(uAxis);
        double baseOffsetV = center.subtract(planeCenter).dot(vAxis);
        double baseFromSourceUx = sourceU.dot(uAxis);
        double baseFromSourceUy = sourceU.dot(vAxis);
        double baseFromSourceVx = sourceV.dot(uAxis);
        double baseFromSourceVy = sourceV.dot(vAxis);
        double beamLayerU = beamWorld.dot(uAxis);
        double beamLayerV = beamWorld.dot(vAxis);
        double sourceExtentU = SOURCE_HALF_SIZE * (Math.abs(baseFromSourceUx) + Math.abs(baseFromSourceVx));
        double sourceExtentV = SOURCE_HALF_SIZE * (Math.abs(baseFromSourceUy) + Math.abs(baseFromSourceVy));
        double halfU = Math.max(1.5, Math.abs(baseOffsetU) + sourceExtentU + Math.abs(beamLayerU) + PROJECTION_PADDING);
        double halfV = Math.max(1.5, Math.abs(baseOffsetV) + sourceExtentV + Math.abs(beamLayerV) + PROJECTION_PADDING);

        return new Projection(planeCenter, planeNormal, uAxis, vAxis, sourceU, sourceV, halfU, halfV,
                beamLayerU, beamLayerV,
                beamWorld.x, beamWorld.y, beamWorld.z,
                baseOffsetU, baseOffsetV,
                baseFromSourceUx, baseFromSourceUy,
                baseFromSourceVx, baseFromSourceVy);
    }

    private static Vec3 stableAxisInPlane(Vec3 normal) {
        Vec3 fallback = Math.abs(normal.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        return fallback.subtract(normal.scale(fallback.dot(normal)));
    }

    record Projection(Vec3 center, Vec3 normal, Vec3 uAxis, Vec3 vAxis, Vec3 sourceU, Vec3 sourceV,
                      double halfU, double halfV,
                      double beamLayerU, double beamLayerV,
                      double beamWorldX, double beamWorldY, double beamWorldZ,
                      double baseOffsetU, double baseOffsetV,
                      double baseFromSourceUx, double baseFromSourceUy,
                      double baseFromSourceVx, double baseFromSourceVy) {
        Vec3 corner(int uSign, int vSign) {
            return center.add(uAxis.scale(uSign * halfU)).add(vAxis.scale(vSign * halfV));
        }

        float localU(int sign) {
            return (float) (sign * halfU);
        }

        float localV(int sign) {
            return (float) (sign * halfV);
        }
    }

    record MeshVertex(float sourceU, float sourceV, float beamT, float r, float g, float b, float a) {}

    private record Color(float r, float g, float b, float a) {
        static Color fromArgb(int argb) {
            return new Color(
                    ((argb >> 16) & 0xFF) / 255f,
                    ((argb >> 8) & 0xFF) / 255f,
                    (argb & 0xFF) / 255f,
                    ((argb >>> 24) & 0xFF) / 255f);
        }
    }

    private static final class MeshBeamRenderer {
        private final VertexArray vertexArray = VertexArray.create();

        boolean draw(RenderType renderType, MeshVertex[] vertices, Runnable uploadUniforms) {
            if (renderType == null || vertices.length == 0) return false;
            BufferBuilder builder = RenderSystem.renderThreadTesselator()
                    .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (MeshVertex vertex : vertices) {
                builder.addVertex(vertex.sourceU, vertex.sourceV, vertex.beamT)
                        .setColor(vertex.r, vertex.g, vertex.b, vertex.a);
            }
            MeshData mesh = builder.buildOrThrow();
            vertexArray.upload(mesh, VertexArray.DrawUsage.STREAM);
            vertexArray.bind();
            vertexArray.setup(renderType);
            uploadUniforms.run();
            RenderSystem.enableBlend();
            RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
            RenderSystem.blendFunc(GL11.GL_ONE, GL11.GL_ONE);
            RenderSystem.depthMask(false);
            try {
                vertexArray.draw();
            } finally {
                RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
                vertexArray.clear(renderType);
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableBlend();
                RenderSystem.depthMask(true);
                VertexArray.unbind();
            }
            return true;
        }
    }

    private static final class Effect {
        Vec3 center;
        Vec3 sourceNormal;
        Vec3 worldRayDir;
        Vec3 sourceU;
        Vec3 sourceV;
        int[] symbolLayerSnapshot;
        MeshVertex[] meshVertices;
        int sourceWidth;
        int sourceHeight;
        int color;
        int lifetime;
        double beamHeight;
        int fadeInTicks;
        int age;
        int ticksSinceRefresh;
        int sourceEntityId = -1;
        long lifecycleEndTick = Long.MIN_VALUE;
        int lifecycleDurationTicks;

        Effect(Vec3 center, Vec3 sourceNormal, Vec3 worldRayDir,
               Vec3 sourceU, Vec3 sourceV,
               MeshVertex[] meshVertices, int sourceWidth, int sourceHeight,
               int color, int lifetime, double beamHeight, int fadeInTicks) {
            this.center = center;
            refresh(sourceNormal, worldRayDir, sourceU, sourceV, meshVertices,
                    sourceWidth, sourceHeight, color, lifetime, beamHeight, fadeInTicks, false);
        }

        void refresh(Vec3 sourceNormal, Vec3 worldRayDir, Vec3 sourceU, Vec3 sourceV,
                     MeshVertex[] meshVertices, int sourceWidth, int sourceHeight,
                     int color, int lifetime, double beamHeight, int fadeInTicks,
                     boolean preserveLifetime) {
            if (sourceNormal.lengthSqr() < 1.0E-10) {
                throw new IllegalArgumentException("Source surface normal must be non-zero");
            }
            this.sourceNormal = sourceNormal.normalize();
            this.worldRayDir = worldRayDir;
            this.sourceU = sourceU;
            this.sourceV = sourceV;
            this.meshVertices = meshVertices;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.color = color;
            this.beamHeight = beamHeight;
            this.fadeInTicks = Math.max(0, fadeInTicks);
            if (preserveLifetime) {
                inheritRemainingLifetime(lifetime);
            } else {
                this.lifetime = lifetime;
                this.ticksSinceRefresh = 0;
            }
        }

        void inheritRemainingLifetime(int reportedRemaining) {
            this.lifetime = inheritedDeadline(
                    ticksSinceRefresh, lifetime, reportedRemaining);
        }

        void bindEndTick(long endTick, int durationTicks) {
            lifecycleEndTick = lifecycleEndTick == Long.MIN_VALUE
                    ? endTick
                    : Math.min(lifecycleEndTick, endTick);
            lifecycleDurationTicks = Math.max(1, durationTicks);
        }

        boolean expired(long gameTime) {
            return lifecycleEndTick != Long.MIN_VALUE && gameTime != Long.MIN_VALUE
                    ? gameTime >= lifecycleEndTick
                    : ticksSinceRefresh >= lifetime;
        }
    }

    static int inheritedDeadline(int elapsed, int currentDeadline, int reportedRemaining) {
        int currentRemaining = Math.max(0, currentDeadline - elapsed);
        return elapsed + Math.min(currentRemaining, Math.max(0, reportedRemaining));
    }
}
