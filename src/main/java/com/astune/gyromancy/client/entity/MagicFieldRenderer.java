package com.astune.gyromancy.client.entity;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.client.effect.EntityEffect;
import com.astune.gyromancy.entity.field.MagicFieldEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Default renderer for stationary magic fields.
 *
 * <p>The supplied Photon effect is emitted at most once per game tick. Its
 * emission probability is the field's average energy density (clamped to the
 * unit interval), and each emission is placed with rejection sampling inside
 * the shape's oriented world-space bounds. The renderer deliberately does not
 * keep a single effect whose size is updated every frame.</p>
 */
public class MagicFieldRenderer<T extends MagicFieldEntity> extends EntityRenderer<T> {
    private static final int MIN_SAMPLE_ATTEMPTS = 4;
    private static final Set<MagicFieldRenderer<?>> INSTANCES =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private final ResourceLocation effectId;
    protected final Map<T, Integer> lastEmissionTick = new WeakHashMap<>();
    protected final Map<T, List<EntityEffect>> emittedEffects = new WeakHashMap<>();

    private int maxSample = MIN_SAMPLE_ATTEMPTS;

    public MagicFieldRenderer(EntityRendererProvider.Context context, String effect) {
        this(context, ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, effect));
    }

    public MagicFieldRenderer(EntityRendererProvider.Context context, ResourceLocation effectId) {
        super(context);
        this.effectId = effectId;
        INSTANCES.add(this);
    }

    /** Clears renderer-owned one-shot effects before a client level change. */
    public static void clearClientState() {
        for (MagicFieldRenderer<?> renderer : Set.copyOf(INSTANCES)) renderer.clearEffects();
    }

    protected void clearEffects() {
        for (List<EntityEffect> effects : emittedEffects.values()) {
            for (EntityEffect effect : effects) effect.stop();
        }
        emittedEffects.clear();
        lastEmissionTick.clear();
    }

    protected void postRender(T entity, float entityYaw, float partialTick, PoseStack poseStack,
                              MultiBufferSource bufferSource, int packedLight){}

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        if (!entity.isAlive()) {
            cleanup(entity);
            return;
        }

        cleanupFinishedEffects(entity);
        // Entity renderers can be called more than once for a game tick. The
        // field emits according to tick semantics, not frame rate.
        Integer previousTick = lastEmissionTick.put(entity, entity.tickCount);
        if (previousTick != null && previousTick == entity.tickCount) return;

        if (entity.getRandom().nextDouble() >= emissionProbability(entity.averageEnergy())) return;
        Vec3 point = randomPointInside(entity, entity.getRandom());
        if (point == null) return;

        EntityEffect effect = new EntityEffect(entity, effectId)
                .setOffset(point.subtract(entity.position()));
        effect.start();
        // Keep the wrapper until the next render even if the resource was not
        // available.  This lets a runtime that has just been emitted finish
        // normally, while the cleanup path still releases failed emissions.
        emittedEffects.computeIfAbsent(entity, ignored -> new ArrayList<>()).add(effect);

        postRender(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /** Converts average energy density into a per-tick emission probability. */
    public static double emissionProbability(double averageEnergy) {
        if (!Double.isFinite(averageEnergy) || averageEnergy <= 0.0) return 0.0;
        return min(1.0, averageEnergy);
    }

    /**
     * Samples a world-space point from the exact shape, not just its AABB.
     * The fallback is the field centre, which every valid centred shape must
     * contain; a custom shape that violates that contract simply emits no FX.
     */
    @Nullable
    protected Vec3 randomPointInside(T entity, RandomSource random) {
        AABB bounds = entity.fieldBounds();
        maxSample = (int) max(MIN_SAMPLE_ATTEMPTS, entity.shape().volume() * 4);
        for (int attempt = 0; attempt < maxSample; attempt++) {
            Vec3 point = new Vec3(
                    bounds.minX + random.nextDouble() * bounds.getXsize(),
                    bounds.minY + random.nextDouble() * bounds.getYsize(),
                    bounds.minZ + random.nextDouble() * bounds.getZsize());
            if (entity.isInside(point)) return point;
        }

        Vec3 centre = entity.position();
        return entity.isInside(centre) ? centre : null;
    }

    private void cleanupFinishedEffects(T entity) {
        List<EntityEffect> effects = emittedEffects.get(entity);
        if (effects == null) return;
        effects.removeIf(effect -> {
            if (effect.isAlive()) return false;
            effect.stop();
            return true;
        });
        if (effects.isEmpty()) emittedEffects.remove(entity);
    }

    private void cleanup(T entity) {
        lastEmissionTick.remove(entity);
        List<EntityEffect> effects = emittedEffects.remove(entity);
        if (effects != null) {
            for (EntityEffect effect : effects) effect.stop();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return null;
    }
}
