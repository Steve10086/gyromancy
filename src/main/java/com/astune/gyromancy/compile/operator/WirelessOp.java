package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.compile.CompileDiagnostic;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.GroupNode;
import com.astune.gyromancy.array.compile.MissingDependency;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.array.compile.RegisteredOp;
import com.astune.gyromancy.array.compile.SourceFrameRef;
import com.astune.gyromancy.array.compile.StaticResolveContext;
import com.astune.gyromancy.array.compile.StructureResolution;
import com.astune.gyromancy.array.runtime.ArrayEffectLifecycle;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import com.astune.gyromancy.array.runtime.OpRuntimeFailure;
import com.astune.gyromancy.array.runtime.RuntimeHandle;
import com.astune.gyromancy.array.runtime.wireless.WirelessDependencyCoordinator;
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
public final class WirelessOp implements PersistentOp, DynamicStructure {
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
    public StructureResolution resolveStructure(StaticResolveContext context) {
        if (publishesSource()) return new StructureResolution.Preserved(Set.of());
        if (context == null || context.level() == null || context.manager() == null) {
            return new StructureResolution.Preserved(Set.of());
        }
        if (context.contains(key)) {
            return new StructureResolution.FatalFailure(List.of(new CompileDiagnostic(
                    "wireless_cycle", "Wireless dependency cycle at " + key)));
        }

        WirelessRegistry registry = context.level().getData(ModAttachments.WIRELESS_REGISTRY);
        Optional<WirelessRegistry.Value> value = registry.value(key);
        if (value.isEmpty()) {
            return new StructureResolution.RetryableDependency(List.of(
                    new MissingDependency(key, boundary.glyphUuid())));
        }

        Optional<GroupNode> source = value.get().loadedSource(context.level(), context.manager());
        if (source.isEmpty()) {
            return new StructureResolution.RetryableDependency(List.of(
                    new MissingDependency(key, boundary.glyphUuid())));
        }

        GroupNode sourceGroup = source.get();
        return new StructureResolution.Found(
                sourceGroup,
                SourceFrameRef.fromGroup(sourceGroup),
                Set.of(key));
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
            WirelessDependencyCoordinator.retry(level, key);
            return new RuntimeHandle(Map.of());
        }

        OpRuntimeFailure.terminate(context, this, OpRuntimeFailure.Kind.RUNTIME_ERROR,
                "Wireless consumer reached runtime without static structure replacement");
        return new RuntimeHandle(java.util.Map.of());
    }

    @Override
    public void deactivate(OpRuntimeContext context, Map<String, Object> scratchData) {
        if (context == null || context.level() == null || !publishesSource()) return;
        WirelessRegistry registry = context.level().getData(ModAttachments.WIRELESS_REGISTRY);
        Set<UUID> dependents = registry.unpublish(
                key, WirelessRegistry.Value.fromSource(publishedSource));
        for (UUID dependent : dependents) {
            ArrayEffectLifecycle.recompose(context.level(), dependent);
        }
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
