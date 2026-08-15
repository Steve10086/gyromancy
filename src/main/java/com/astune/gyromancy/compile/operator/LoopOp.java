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
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import com.astune.gyromancy.array.runtime.RuntimeHandle;
import com.astune.gyromancy.array.runtime.emit.EmitResult;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.registry.ModAttachments;
import com.astune.gyromancy.symbol.SymbolCatalog;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Re-activates a persistent child effect when its bound effect disappears. */
@RegisteredOp
public final class LoopOp extends OnEntityTickOp implements PersistentOp {
    private static final int CHECK_INTERVAL = 10;
    private static final Map<ServerLevel, Set<LoopOp>> ACTIVE = new WeakHashMap<>();

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
            return List.of(OpInputMatcher.op(PersistentOp.class));
        }

        @Override
        public CompileResult<CompiledOp> compile(PositionedGlyph boundary,
                                                  List<OpInput> matchedInputs,
                                                  List<OpInput> inputs) {
            PersistentOp child = null;
            for (OpInput input : inputs) {
                if (!(input instanceof OpInput.Op op)
                        || !(op.operator() instanceof PersistentOp persistent)) continue;
                if (child != null) {
                    return new CompileResult.Failure<>(List.of(
                            new com.astune.gyromancy.array.compile.CompileDiagnostic(
                                    "multiple_loop_effects",
                                    "Loop accepts exactly one persistent effect")));
                }
                child = persistent;
            }
            if (child == null) {
                return new CompileResult.Failure<>(List.of(
                        new com.astune.gyromancy.array.compile.CompileDiagnostic(
                                "missing_loop_effect",
                                "Loop requires a nested persistent effect")));
            }
            return new CompileResult.Success<>(new LoopOp(
                    boundary, matchedInputs, inputs, child));
        }
    };

    private final PositionedGlyph boundary;
    private final List<OpInput> matchedInputs;
    private final List<OpInput> inputs;
    private final PersistentOp child;
    private UUID boundArrayId;
    private Set<String> childScratchKeys = Set.of();

    /** Payload-only instance created when an entity is loaded from NBT. */
    private LoopOp() {
        this(null, List.of(), List.of(), null);
    }

    private LoopOp(PositionedGlyph boundary, List<OpInput> matchedInputs,
                   List<OpInput> inputs, PersistentOp child) {
        this.boundary = boundary;
        this.matchedInputs = List.copyOf(matchedInputs);
        this.inputs = List.copyOf(inputs);
        this.child = child;
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
    public void contributeEntityPayloads(List<EntityPayload> payloads) {
        payloads.add(this);
    }

    @Override
    public void bindToArray(UUID arrayId) {
        boundArrayId = arrayId;
    }

    @Override
    public void onEntityTick(EntityTickContext ctx) {
        if (ctx.isClientSide() || boundArrayId == null
                || ctx.tickCount() % CHECK_INTERVAL != 0
                || child == null) return;
        if (ctx.level() instanceof ServerLevel level) checkAndRestart(level);
    }

    @Override
    public RuntimeHandle activate(OpRuntimeContext context) {
        RuntimeHandle handle = activateChild(context);
        register(context.level(), this);
        return handle;
    }

    @Override
    public void deactivate(OpRuntimeContext context, Map<String, Object> scratchData) {
        unregister(context.level(), this);
        if (child != null) child.deactivate(childContext(context), scratchData);
    }

    /** Called from the common server tick after entity effects have ticked. */
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.getGameTime() % CHECK_INTERVAL != 0) continue;
            Set<LoopOp> loops = ACTIVE.get(level);
            if (loops == null || loops.isEmpty()) continue;
            for (LoopOp loop : List.copyOf(loops)) loop.checkAndRestart(level);
        }
    }

    private RuntimeHandle activateChild(OpRuntimeContext context) {
        RuntimeHandle handle = child.activate(childContext(context));
        childScratchKeys = Set.copyOf(handle.scratchData().keySet());
        return handle;
    }

    private OpRuntimeContext childContext(OpRuntimeContext context) {
        ArrayObject array = null;
        if (context.level() != null) {
            array = context.level().getData(ModAttachments.ARRAY_MANAGER)
                    .getArrayForGlyph(boundary.glyphUuid());
        }
        if (array == null) array = context.array();

        OpRuntimeContext childContext = context.forOp(child);
        if (array == null) return childContext;

        PositionedGlyph rootGlyph = context.arrayRootGlyph() != null
                ? context.arrayRootGlyph() : boundary;
        return childContext.withArray(array, rootGlyph);
    }

    private void checkAndRestart(ServerLevel level) {
        MagicArrayManager manager = level.getData(ModAttachments.ARRAY_MANAGER);
        ArrayObject array = boundArrayId == null
                ? manager.getArrayForGlyph(boundary.glyphUuid())
                : manager.getArrayObj(boundArrayId);
        if (array == null || hasLiveChildEffect(level, array.scratchData())) return;

        Set<String> oldChildScratchKeys = childScratchKeys;
        RuntimeHandle handle = activateChild(new OpRuntimeContext(level, child));
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
