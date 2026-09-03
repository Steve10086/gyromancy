package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.array.MagicArrayManager;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.array.compile.RegisteredOp;
import com.astune.gyromancy.array.runtime.ArrayEffectLifecycle;
import com.astune.gyromancy.array.runtime.OpRuntimeFailure;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import com.astune.gyromancy.array.runtime.RuntimeHandle;
import com.astune.gyromancy.array.runtime.emit.EmitResult;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.registry.ModAttachments;
import com.astune.gyromancy.symbol.SymbolCatalog;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Re-activates a persistent child effect either on lifecycle loss or, when
 * attached as an entity payload, on a distance-adjusted fixed interval.
 */
@RegisteredOp
public final class LoopOp extends OnEntityTickOp implements PersistentOp {
    private static final int CHECK_INTERVAL = 10;
    private static final int MIN_PAYLOAD_INTERVAL = 5;
    private static final Map<ServerLevel, Set<LoopOp>> ACTIVE = new WeakHashMap<>();
    private static final List<OpInputMatcher> CHILD_MATCHERS =
            List.of(OpInputMatcher.op(PersistentOp.class));

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "loop");
    private static final ResourceLocation LOOP_SYMBOL =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "loop");
    public static final Codec<LoopOp> CODEC = Codec.unit(LoopOp::new);

    public static final OpDefinition DEFINITION = new OpDefinition() {
        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public List<OpInputMatcher> match() {
            return List.of(OpInputMatcher.rune("loop"));
        }

        @Override
        public List<OpInputMatcher> accepted() {
            return CHILD_MATCHERS;
        }

        @Override
        public CompileResult<CompiledOp> compile(PositionedGlyph boundary,
                                                  List<OpInput> matchedInputs,
                                                  List<OpInput> inputs) {
            List<OpInput> children = inputs.stream()
                    .filter(OpInput.Op.class::isInstance)
                    .toList();
            if (children.size() != 1) {
                return new CompileResult.Failure<>(List.of(
                        new com.astune.gyromancy.array.compile.CompileDiagnostic(
                                children.isEmpty() ? "missing_loop_effect" : "multiple_loop_effects",
                                "Loop requires exactly one nested persistent effect")));
            }
            return new CompileResult.Success<>(new LoopOp(
                    boundary, matchedInputs, inputs, children.getFirst()));
        }
    };

    private final PositionedGlyph boundary;
    private final List<OpInput> matchedInputs;
    private final List<OpInput> inputs;
    private final OpInput childInput;
    private UUID boundArrayId;
    private Set<String> childScratchKeys = Set.of();
    private boolean assignedAsEntityPayload;
    private PersistentOp activeChild;
    private OpRuntimeContext activeChildContext;

    /** Payload-only instance created when an entity is loaded from NBT. */
    private LoopOp() {
        this(null, List.of(), List.of(), null);
    }

    private LoopOp(PositionedGlyph boundary, List<OpInput> matchedInputs,
                   List<OpInput> inputs, OpInput childInput) {
        this.boundary = boundary;
        this.matchedInputs = List.copyOf(matchedInputs);
        this.inputs = List.copyOf(inputs);
        this.childInput = childInput;
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public ResourceLocation typeId() {
        return ID;
    }

    @Override
    protected Codec<LoopOp> codec() {
        return CODEC;
    }

    @Override
    public PositionedGlyph boundary() {
        return boundary;
    }

    @Override
    public List<OpInput> inputs() {
        return inputs;
    }

    public List<OpInput> matchedInputs() {
        return matchedInputs;
    }

    @Override
    public int color() {
        return SymbolCatalog.glyphColorFor(LOOP_SYMBOL);
    }

    @Override
    public void contributeEntityPayloads(List<EntityPayload> payloads, OpRuntimeContext context) {
        assignedAsEntityPayload = true;
        payloads.add(this);
    }

    @Override
    public void bindToArray(UUID arrayId) {
        boundArrayId = arrayId;
    }

    @Override
    public void onEntityTick(EntityTickContext ctx) {
        if (ctx.isClientSide() || boundArrayId == null
                || childInput == null) return;
        if (!(ctx.level() instanceof ServerLevel level)) return;

        ArrayObject array = level.getData(ModAttachments.ARRAY_MANAGER).getArrayObj(boundArrayId);
        if (array == null) return;

        int interval = payloadInterval(ctx, array);
        if (ctx.tickCount() % interval != 0) return;
        triggerFromEntity(level, ctx, array);
    }

    @Override
    public RuntimeHandle activate(OpRuntimeContext context) {
        RuntimeHandle handle = activateChild(context);
        if (!assignedAsEntityPayload) register(context.level(), this);
        return handle;
    }

    @Override
    public void deactivate(OpRuntimeContext context, Map<String, Object> scratchData) {
        unregister(context.level(), this);
        if (activeChild != null) {
            OpRuntimeContext runtime = activeChildContext == null
                    ? childContext(context).forOp(activeChild) : activeChildContext;
            activeChild.deactivate(runtime, scratchData);
            activeChild = null;
            activeChildContext = null;
            return;
        }
        if (childInput == null) return;
        OpResolution resolved = resolveChild(context);
        if (isPersistent(resolved) && resolved.operator() instanceof PersistentOp persistent) {
            persistent.deactivate(
                    resolved.runtimeContextOr(childContext(context)).forOp(persistent),
                    scratchData);
        }
    }

    /** Called from the common server tick after entity effects have ticked. */
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.getGameTime() % CHECK_INTERVAL != 0) continue;
            Set<LoopOp> loops = ACTIVE.get(level);
            if (loops == null || loops.isEmpty()) continue;
            for (LoopOp loop : List.copyOf(loops)) {
                if (!loop.assignedAsEntityPayload) loop.checkAndRestart(level);
            }
        }
    }

    /** Re-activates the child directly for the entity that owns this payload. */
    private void triggerFromEntity(ServerLevel level, EntityTickContext ctx, ArrayObject array) {
        if (childInput == null) return;

        Vec3 emissionNormal = ctx.arrayFrame() == null
                ? array.rootCircleGlyph().surface().normal()
                : ctx.arrayFrame().normal();
        OpRuntimeContext context = new OpRuntimeContext(
                level, this, ctx.position(), emissionNormal)
                .withArray(array, array.rootCircleGlyph())
                .withParent(ctx.owner());
        RuntimeHandle handle = activateChild(context);
        if (boundArrayId != null) {
            ArrayEffectLifecycle.bindEmittedEntities(level, boundArrayId, handle.scratchData());
        }
    }

    private int payloadInterval(EntityTickContext ctx, ArrayObject array) {
        PositionedGlyph liveBoundary = liveBoundary(array);
        if (liveBoundary == null) return CHECK_INTERVAL;

        double referenceDistance = Math.max(liveBoundary.length(), liveBoundary.width());
        if (!Double.isFinite(referenceDistance) || referenceDistance <= 1.0E-6) {
            return CHECK_INTERVAL;
        }

        double distance = liveBoundary.center().distanceTo(ctx.position());
        double ratio = Math.max(0.0, Math.min(1.0, distance / referenceDistance));
        return Math.max(MIN_PAYLOAD_INTERVAL,
                (int) Math.round(CHECK_INTERVAL
                        - ratio * (CHECK_INTERVAL - MIN_PAYLOAD_INTERVAL)));
    }

    private PositionedGlyph liveBoundary(ArrayObject array) {
        if (boundary == null) return null;
        if (array == null) return boundary;
        if (array.rootCircleGlyph().glyphUuid().equals(boundary.glyphUuid())) {
            return array.rootCircleGlyph();
        }
        return array.boundGlyphs().stream()
                .filter(glyph -> glyph.glyphUuid().equals(boundary.glyphUuid()))
                .findFirst()
                .orElse(boundary);
    }

    private RuntimeHandle activateChild(OpRuntimeContext context) {
        if (childInput == null) return new RuntimeHandle(Map.of());
        OpResolution resolved = resolveChild(context);
        OpRuntimeContext runtime = resolved.runtimeContextOr(childContext(context));
        if (!isPersistent(resolved) || !(resolved.operator() instanceof PersistentOp persistent)) {
            childScratchKeys = Set.of();
            OpRuntimeFailure.terminate(runtime, this, OpRuntimeFailure.Kind.RUNTIME_ERROR,
                    "Loop requires a PersistentOp after dynamic resolution");
            return new RuntimeHandle(Map.of());
        }
        OpRuntimeContext persistentContext = runtime.forOp(persistent);
        RuntimeHandle handle = persistent.activate(persistentContext);
        activeChild = persistent;
        activeChildContext = persistentContext;
        childScratchKeys = Set.copyOf(handle.scratchData().keySet());
        return handle;
    }

    private OpResolution resolveChild(OpRuntimeContext context) {
        OpRuntimeContext runtime = childContext(context);
        return OpResolver.resolve(childInput, OpResolveContext.forRuntime(
                this, OpResolveContext.UseSite.PERSISTENT_CHILD, runtime, boundary));
    }

    private static boolean isPersistent(OpResolution resolution) {
        return OpInputMatcher.anyMatches(CHILD_MATCHERS, resolution);
    }

    private OpRuntimeContext childContext(OpRuntimeContext context) {
        OpRuntimeContext base = context == null ? OpRuntimeContext.empty() : context;
        ArrayObject array = null;
        if (base.level() != null) {
            MagicArrayManager manager = base.level().getData(ModAttachments.ARRAY_MANAGER);
            if (boundArrayId != null) array = manager.getArrayObj(boundArrayId);
            if (array == null && boundary != null) {
                array = manager.getArrayForGlyph(boundary.glyphUuid());
            }
        }
        if (array == null) array = base.array();

        OpRuntimeContext childContext = base.forOp(this);
        if (array == null) return childContext;

        PositionedGlyph rootGlyph = base.arrayRootGlyph() != null
                ? base.arrayRootGlyph() : array.rootCircleGlyph();
        return childContext.withArray(array, rootGlyph);
    }

    private void checkAndRestart(ServerLevel level) {
        MagicArrayManager manager = level.getData(ModAttachments.ARRAY_MANAGER);
        ArrayObject array = boundArrayId == null
                ? manager.getArrayForGlyph(boundary.glyphUuid())
                : manager.getArrayObj(boundArrayId);
        if (array == null || hasLiveChildEffect(level, array.scratchData())) return;

        Set<String> oldChildScratchKeys = childScratchKeys;
        RuntimeHandle handle = activateChild(new OpRuntimeContext(level, this));
        Map<String, Object> updated = new HashMap<>(array.scratchData());
        for (String key : oldChildScratchKeys) updated.remove(key);
        updated.putAll(handle.scratchData());
        manager.setArrayScratchData(array.arrayId(), updated);
        ArrayEffectLifecycle.bindEmittedEntities(level, array.arrayId(), handle.scratchData());
    }

    private boolean hasLiveChildEffect(ServerLevel level, Map<String, Object> scratchData) {
        for (String key : childScratchKeys) {
            Object value = scratchData.get(key);
            if (value instanceof ArrayObject.EntityRef ref && isAlive(level, ref)) return true;
            if (EmitResult.EMISSIONS_KEY.equals(key) && value instanceof List<?> emissions) {
                for (Object emission : emissions) {
                    if (emission instanceof com.astune.gyromancy.array.runtime.emit.EmittedObject emitted
                            && emitted.ref() instanceof ArrayObject.EntityRef ref
                            && isAlive(level, ref)) return true;
                }
            }
        }
        return false;
    }

    private static boolean isAlive(ServerLevel level, ArrayObject.EntityRef ref) {
        return ref.resolve(level) instanceof net.minecraft.world.entity.Entity entity
                && entity.isAlive();
    }

    private static void register(ServerLevel level, LoopOp loop) {
        ACTIVE.computeIfAbsent(level, ignored -> java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<>())).add(loop);
    }

    private static void unregister(ServerLevel level, LoopOp loop) {
        Set<LoopOp> loops = ACTIVE.get(level);
        if (loops == null) return;
        loops.remove(loop);
        if (loops.isEmpty()) ACTIVE.remove(level);
    }
}
