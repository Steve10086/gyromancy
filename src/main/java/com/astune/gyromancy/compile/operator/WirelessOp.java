package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.array.MagicArrayManager;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.compile.CompileDiagnostic;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.GlobalCompiler;
import com.astune.gyromancy.array.compile.GroupNode;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.array.compile.RegisteredOp;
import com.astune.gyromancy.array.runtime.ArrayEffectLifecycle;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import com.astune.gyromancy.array.runtime.OpRuntimeFailure;
import com.astune.gyromancy.array.runtime.RuntimeHandle;
import com.astune.gyromancy.array.runtime.wireless.WirelessRegistry;
import com.astune.gyromancy.registry.ModAttachments;
import com.astune.gyromancy.symbol.SecretText;
import com.astune.gyromancy.symbol.SecretTextSymbol;
import com.astune.gyromancy.symbol.SymbolCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Publishes a direct source circle under a secret-text key, or dynamically
 * replaces itself with the already-published source when consumed by a parent.
 */
@RegisteredOp
public final class WirelessOp implements PersistentOp, OpResolvable {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "wireless");

    private static final List<OpInputMatcher> SOURCE_MATCHERS = List.of(
            OpInputMatcher.boundary(SymbolRole.OUTER_CIRCLE),
            OpInputMatcher.rawGroup(SymbolRole.OUTER_CIRCLE));

    public static final OpDefinition DEFINITION = new OpDefinition() {
        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public List<OpInputMatcher> match() {
            return List.of(OpInputMatcher.rune("space"), OpInputMatcher.secretText());
        }

        @Override
        public List<OpInputMatcher> accepted() {
            return List.of(OpInputMatcher.secretText(),
                    SOURCE_MATCHERS.getFirst(), SOURCE_MATCHERS.getLast());
        }

        @Override
        public CompileResult<CompiledOp> compile(PositionedGlyph boundary,
                                                  List<OpInput> matchedInputs,
                                                  List<OpInput> inputs) {
            List<GroupNode> sources = directSourceGroups(inputs);
            if (sources.size() > 1) {
                return new CompileResult.Failure<>(List.of(new CompileDiagnostic(
                        "ambiguous_wireless_source",
                        "Wireless accepts at most one direct outer-circle source")));
            }
            List<SecretText> secretTexts = secretTexts(inputs);
            if (secretTexts.isEmpty()) {
                return new CompileResult.Failure<>(List.of(new CompileDiagnostic(
                        "missing_wireless_key", "Wireless requires at least one secret-text rune")));
            }
            return new CompileResult.Success<>(new WirelessOp(boundary, matchedInputs, inputs,
                    WirelessRegistry.keyFor(secretTexts),
                    sources.isEmpty() ? null : sources.getFirst()));
        }
    };

    private final PositionedGlyph boundary;
    private final List<OpInput> matchedInputs;
    private final List<OpInput> inputs;
    private final String key;
    private final GroupNode publishedSource;
    private PersistentOp activeDelegate;
    private OpRuntimeContext activeDelegateContext;

    private WirelessOp(PositionedGlyph boundary, List<OpInput> matchedInputs,
                       List<OpInput> inputs, String key, GroupNode publishedSource) {
        this.boundary = boundary;
        this.matchedInputs = List.copyOf(matchedInputs);
        this.inputs = List.copyOf(inputs);
        this.key = key;
        this.publishedSource = publishedSource;
    }

    /** Exposes the fixed public secret-text key function used by this Op. */
    public static String keyForSecretTexts(List<SecretText> secretTexts) {
        return WirelessRegistry.keyFor(secretTexts);
    }

    @Override
    public ResourceLocation id() {
        return ID;
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

    public String key() {
        return key;
    }

    public boolean publishesSource() {
        return publishedSource != null;
    }

    @Override
    public int color() {
        return SymbolCatalog.glyphColorFor(ResourceLocation.fromNamespaceAndPath(
                Gyromancy.MODID, "space"));
    }

    /**
     * A publishing root has no local child effect: activation only updates the
     * level directory and asks each current dependent to follow the normal
     * runtime recompose lifecycle.  A consumer root delegates to the resolved
     * persistent source so the feature is also useful outside another parent.
     */
    @Override
    public RuntimeHandle activate(OpRuntimeContext context) {
        if (context == null || context.level() == null) {
            return new RuntimeHandle(Map.of());
        }
        ServerLevel level = context.level();
        if (publishesSource()) {
            WirelessRegistry registry = level.getData(ModAttachments.WIRELESS_REGISTRY);
            Set<UUID> dependents = registry.publish(key, WirelessRegistry.Value.fromSource(publishedSource));
            for (UUID dependent : dependents) {
                ArrayEffectLifecycle.recompose(level, dependent);
            }
            return new RuntimeHandle(Map.of());
        }

        OpResolution resolved = resolve(OpResolveContext.forRuntime(
                this, OpResolveContext.UseSite.GROUP_INPUT, context, boundary));
        if (!(resolved.operator() instanceof PersistentOp persistent) || resolved.operator() == this) {
            OpRuntimeFailure.terminate(context, this, OpRuntimeFailure.Kind.RUNTIME_ERROR,
                    "Wireless key " + key + " is not available as a persistent source");
            return new RuntimeHandle(Map.of());
        }

        OpRuntimeContext delegateContext = resolved.runtimeContextOr(context).forOp(persistent);
        RuntimeHandle handle = persistent.activate(delegateContext);
        activeDelegate = persistent;
        activeDelegateContext = delegateContext;
        return handle;
    }

    @Override
    public void deactivate(OpRuntimeContext context, Map<String, Object> scratchData) {
        if (activeDelegate == null) return;
        OpRuntimeContext delegateContext = activeDelegateContext == null
                ? context.forOp(activeDelegate) : activeDelegateContext;
        activeDelegate.deactivate(delegateContext, scratchData);
        activeDelegate = null;
        activeDelegateContext = null;
    }

    /**
     * Runtime lookup keeps static compilation independent of the level map.
     * Vector consumers receive the source group for their own second compiler;
     * all other consumers receive the result of the normal global compiler.
     */
    @Override
    public OpResolution resolve(OpResolveContext context) {
        if (publishesSource() || context == null
                || context.phase() != OpResolveContext.Phase.RUNTIME
                || context.runtimeContext() == null
                || context.runtimeContext().level() == null) {
            return OpResolution.unchanged(this, context);
        }
        ServerLevel level = context.runtimeContext().level();

        WirelessRegistry registry = level.getData(ModAttachments.WIRELESS_REGISTRY);
        Optional<WirelessRegistry.Value> value = registry.value(key);
        if (value.isEmpty()) return OpResolution.unchanged(this, context);

        MagicArrayManager manager = level.getData(ModAttachments.ARRAY_MANAGER);
        Optional<GroupNode> source = value.get().loadedSource(level, manager);
        if (source.isEmpty()) return OpResolution.unchanged(this, context);

        GroupNode sourceGroup = source.get();
        ArrayObject sourceArray = value.get().runtimeArray(sourceGroup);
        OpRuntimeContext sourceRuntime = context.runtimeContext()
                .withArray(sourceArray, sourceGroup.boundary());
        OpResolveContext sourceContext = context.withArray(sourceArray)
                .withRuntimeContext(sourceRuntime)
                .withTargetBoundary(sourceGroup.boundary())
                .withSourceGroup(sourceGroup);

        if (context.array() != null) registry.subscribe(key, context.array().arrayId());
        if (context.useSite() == OpResolveContext.UseSite.VECTOR) {
            return new OpResolution(this, sourceGroup, sourceArray, sourceRuntime);
        }

        CompileResult<CompiledOp> compiled = new GlobalCompiler(manager.opDefinitions())
                .compile(sourceGroup, sourceContext);
        if (compiled instanceof CompileResult.Success<CompiledOp> success) {
            return new OpResolution(success.value(), sourceGroup, sourceArray, sourceRuntime);
        }

        String detail = compiled instanceof CompileResult.Failure<CompiledOp> failure
                ? failure.diagnostics().toString() : "unknown compile result";
        OpRuntimeFailure.terminate(context.runtimeContext(), this, OpRuntimeFailure.Kind.RUNTIME_ERROR,
                "Wireless key " + key + " could not compile: " + detail);
        return OpResolution.unchanged(this, context);
    }

    private static List<GroupNode> directSourceGroups(List<OpInput> inputs) {
        List<GroupNode> sources = new ArrayList<>();
        for (OpInput input : inputs) {
            if (input instanceof OpInput.Op op && op.sourceGroup() != null
                    && op.sourceGroup().boundary().role() == SymbolRole.OUTER_CIRCLE) {
                sources.add(op.sourceGroup());
            } else if (input instanceof OpInput.RawGroup raw
                    && raw.boundary() != null
                    && raw.boundary().role() == SymbolRole.OUTER_CIRCLE) {
                sources.add(raw.group());
            }
        }
        return List.copyOf(sources);
    }

    private static List<SecretText> secretTexts(List<OpInput> inputs) {
        List<SecretText> secretTexts = new ArrayList<>();
        for (OpInput input : inputs) {
            if (!(input instanceof OpInput.Rune rune)) continue;
            SecretTextSymbol secret = SecretTextSymbol.fromId(rune.glyph().symbolId());
            if (secret != null) secretTexts.add(secret.type());
        }
        return List.copyOf(secretTexts);
    }
}
