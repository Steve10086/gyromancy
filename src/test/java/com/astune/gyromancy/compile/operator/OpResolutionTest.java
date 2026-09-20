package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.GlobalCompiler;
import com.astune.gyromancy.array.compile.GroupNode;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.array.compile.SequenceNode;
import com.astune.gyromancy.array.compile.SymbolNode;
import com.astune.gyromancy.array.compile.VectorCompiler;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import com.astune.gyromancy.compile.vector.StaticVectorOp;
import com.astune.gyromancy.compile.vector.VectorOpDefinitions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpResolutionTest {
    @Test
    void ordinaryOperatorInputsRemainUnchanged() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        StubOp operator = new StubOp("ordinary", circle);
        GroupNode source = group(circle, glyph("arrow", SymbolRole.PARAMETER_RUNE, 2));

        OpResolution resolution = OpResolver.resolve(
                new OpInput.Op(operator, source),
                OpResolveContext.forVector(circle));

        assertSame(operator, resolution.operator());
        assertSame(source, resolution.sourceGroup());
    }

    @Test
    void forwardedSourceUsesTheExistingVectorCompilerPath() {
        PositionedGlyph outer = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 3);
        PositionedGlyph forwarded = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 4);
        GroupNode source = group(forwarded, glyph("arrow", SymbolRole.PARAMETER_RUNE, 5));
        ForwardingOp operator = new ForwardingOp(forwarded, source);

        CompileResult<com.astune.gyromancy.compile.operator.CompiledOp> result =
                new VectorCompiler(VectorOpDefinitions.definitions()).compile(
                        new OpInput.Op(operator, null),
                        OpResolveContext.forVector(outer));

        assertInstanceOf(StaticVectorOp.class,
                assertInstanceOf(CompileResult.Success.class, result).value());
        assertEquals(OpResolveContext.Phase.COMPILE,
                OpResolveContext.forVector(outer).phase());
    }

    @Test
    void deferredInputsPassStaticMatchersButUseConcreteRuntimeMatchers() {
        PositionedGlyph boundary = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 20);
        GroupNode source = group(boundary, glyph("child", SymbolRole.PARAMETER_RUNE, 21));
        PersistentStub persistent = new PersistentStub("persistent", boundary);
        OpInput input = new OpInput.Op(new ResolvingOp(boundary, source, persistent), source);

        assertTrue(OpInputMatcher.rune("arrow").matches(input));
        assertTrue(OpInputMatcher.op(PersistentOp.class).matches(input));
        assertTrue(OpInputMatcher.boundary(SymbolRole.OUTER_CIRCLE).matches(input));
        assertTrue(OpInputMatcher.rawGroup(SymbolRole.OUTER_CIRCLE).matches(input));

        OpResolution resolution = OpResolver.resolve(input, OpResolveContext.forCompile(boundary));
        assertFalse(OpInputMatcher.rune("arrow").matches(resolution));
        assertTrue(OpInputMatcher.op(PersistentOp.class).matches(resolution));
        assertTrue(OpInputMatcher.boundary(SymbolRole.OUTER_CIRCLE).matches(resolution));
        assertTrue(OpInputMatcher.rawGroup(SymbolRole.OUTER_CIRCLE).matches(resolution));
    }

    @Test
    void momentumRecompilesDeferredVectorStructureAtRuntime() {
        PositionedGlyph root = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 30);
        PositionedGlyph motion = glyph("motion", SymbolRole.PARAMETER_RUNE, 31);
        GroupNode compileSource = group(glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 32),
                glyph("arrow_up", SymbolRole.PARAMETER_RUNE, 33, 2.0));
        GroupNode runtimeSource = group(glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 34),
                glyph("arrow_up", SymbolRole.PARAMETER_RUNE, 35, 7.0));
        PhaseForwardingOp deferred = new PhaseForwardingOp(root, compileSource, runtimeSource);
        // A WirelessOp consumer has no compile-time source group; the source
        // is supplied only after its runtime resolution.
        OpInput deferredInput = new OpInput.Op(deferred, null);

        MomentumOp momentum = MomentumOp.compiled(root, List.of(new OpInput.Rune(motion)),
                List.of(new OpInput.Rune(motion), deferredInput),
                new MomentumInputResolver(new VectorCompiler(VectorOpDefinitions.definitions())));

        EmitOp.Emission emission = momentum.modifyEntityEmission(
                new EmitOp.Emission(Vec3.ZERO, 0.0, 1.0F, false), OpRuntimeContext.empty());

        assertEquals(7.0, emission.motionSum(), 1.0E-6);
    }

    @Test
    void nestedMomentumRecompilesDeferredWirelessVectorAtRuntime() {
        PositionedGlyph root = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 36);
        PositionedGlyph motion = glyph("motion", SymbolRole.PARAMETER_RUNE, 37);
        GroupNode compileSource = group(glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 38),
                glyph("arrow", SymbolRole.PARAMETER_RUNE, 39, 2.0));
        GroupNode runtimeSource = group(glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 40),
                glyph("arrow", SymbolRole.PARAMETER_RUNE, 41, 7.0));
        PhaseForwardingOp deferred = new PhaseForwardingOp(root, compileSource, runtimeSource);
        OpInput deferredInput = new OpInput.Op(deferred, null);
        MomentumInputResolver resolver = new MomentumInputResolver(
                new VectorCompiler(VectorOpDefinitions.definitions()));

        MomentumOp nested = MomentumOp.compiled(root, List.of(new OpInput.Rune(motion)),
                List.of(new OpInput.Rune(motion), deferredInput), resolver);
        OpInput nestedInput = new OpInput.Op(nested, compileSource);
        MomentumOp outer = MomentumOp.compiled(root, List.of(new OpInput.Rune(motion)),
                List.of(new OpInput.Rune(motion), nestedInput), resolver);

        List<EntityPayload> payloads = new java.util.ArrayList<>();
        outer.contributeEntityPayloads(payloads, OpRuntimeContext.empty());
        MomentumOp payload = assertInstanceOf(MomentumOp.class, payloads.getFirst());

        Vec3 resolvedAcceleration = payload.accelerationForTick(Vec3.ZERO);
        assertEquals(0.7, resolvedAcceleration.length(), 1.0E-6,
                () -> "resolved acceleration=" + resolvedAcceleration
                        + ", inputs=" + payload.accelerationInputs().size());
    }

    @Test
    void staticParentKeepsDeferredInputAndChecksItsResolvedViewLater() {
        PositionedGlyph outer = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 6);
        PositionedGlyph childBoundary = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 7);
        GroupNode childSource = group(childBoundary,
                glyph("child", SymbolRole.PARAMETER_RUNE, 8));
        PersistentStub resolvedChild = new PersistentStub("resolved_child", childBoundary);

        OpDefinition childDefinition = definition("child",
                List.of(OpInputMatcher.rune("child")),
                (boundary, matched, inputs) -> new CompileResult.Success<>(
                        new ResolvingOp(boundary, childSource, resolvedChild)));
        OpDefinition parentDefinition = definition("parent", List.of(
                        OpInputMatcher.rune("parent")),
                List.of(OpInputMatcher.op(PersistentOp.class)),
                (boundary, matched, inputs) -> new CompileResult.Success<>(
                        new CapturingOp(boundary, inputs)));

        CompileResult<CompiledOp> result = new GlobalCompiler(
                List.of(parentDefinition, childDefinition)).compile(
                        new GroupNode(outer, new SequenceNode(List.of(
                                new SymbolNode(glyph("parent", SymbolRole.PARAMETER_RUNE, 9)),
                                childSource))));

        CapturingOp parent = assertInstanceOf(CapturingOp.class,
                assertInstanceOf(CompileResult.Success.class, result).value());
        OpInput.Op deferredChild = assertInstanceOf(OpInput.Op.class, parent.inputs().get(1));
        assertInstanceOf(ResolvingOp.class, deferredChild.operator());

        OpResolution resolution = OpResolver.resolve(deferredChild,
                OpResolveContext.forCompile(outer));
        assertSame(resolvedChild, resolution.operator());
        assertTrue(OpInputMatcher.op(PersistentOp.class).matches(resolution));
    }

    private static GroupNode group(PositionedGlyph boundary, PositionedGlyph child) {
        return new GroupNode(boundary, new SequenceNode(List.of(new SymbolNode(child))));
    }

    private static PositionedGlyph glyph(String name, SymbolRole role, int id) {
        return glyph(name, role, id, 2.0);
    }

    private static PositionedGlyph glyph(String name, SymbolRole role, int id, double length) {
        BlockPos pos = new BlockPos(0, 64, 0);
        return new PositionedGlyph(
                UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", id)),
                id,
                ResourceLocation.fromNamespaceAndPath("gyromancy", name),
                0.9F,
                role,
                new Vec3(1.0, 0.0, 0.0),
                length,
                1.0,
                pos,
                0.0,
                4.0,
                0.0,
                4.0,
                Set.of(new PixelPos(pos, Direction.NORTH, id, id, 0xFF00AA00)));
    }

    private static OpDefinition definition(String runeName, List<OpInputMatcher> match,
                                           OpDefinitionCompiler compiler) {
        return definition(runeName, match, List.of(), compiler);
    }

    private static OpDefinition definition(String runeName, List<OpInputMatcher> match,
                                           List<OpInputMatcher> accepted,
                                           OpDefinitionCompiler compiler) {
        return new OpDefinition() {
            @Override
            public ResourceLocation id() {
                return ResourceLocation.fromNamespaceAndPath("gyromancy", runeName + "_definition");
            }

            @Override
            public List<OpInputMatcher> match() {
                return match;
            }

            @Override
            public List<OpInputMatcher> accepted() {
                return accepted;
            }

            @Override
            public CompileResult<CompiledOp> compile(PositionedGlyph boundary,
                                                      List<OpInput> matchedInputs,
                                                      List<OpInput> inputs) {
                return compiler.compile(boundary, matchedInputs, inputs);
            }
        };
    }

    @FunctionalInterface
    private interface OpDefinitionCompiler {
        CompileResult<CompiledOp> compile(PositionedGlyph boundary,
                                           List<OpInput> matchedInputs,
                                           List<OpInput> inputs);
    }

    private static class StubOp implements CompiledOp {
        private final ResourceLocation id;
        private final PositionedGlyph boundary;

        private StubOp(String id, PositionedGlyph boundary) {
            this.id = ResourceLocation.fromNamespaceAndPath("gyromancy", id);
            this.boundary = boundary;
        }

        @Override
        public ResourceLocation id() {
            return id;
        }

        @Override
        public PositionedGlyph boundary() {
            return boundary;
        }

        @Override
        public List<OpInput> inputs() {
            return List.of();
        }

        @Override
        public int color() {
            return 0;
        }
    }

    private static final class ForwardingOp extends StubOp implements OpResolvable {
        private final GroupNode source;

        private ForwardingOp(PositionedGlyph boundary, GroupNode source) {
            super("forwarding", boundary);
            this.source = source;
        }

        @Override
        public OpResolution resolve(OpResolveContext context) {
            return new OpResolution(this, source, null, null);
        }
    }

    private static final class ResolvingOp extends StubOp implements OpResolvable {
        private final GroupNode source;
        private final PersistentStub replacement;

        private ResolvingOp(PositionedGlyph boundary, GroupNode source,
                            PersistentStub replacement) {
            super("resolving", boundary);
            this.source = source;
            this.replacement = replacement;
        }

        @Override
        public OpResolution resolve(OpResolveContext context) {
            return new OpResolution(replacement, source, null, null);
        }
    }

    private static final class PhaseForwardingOp extends StubOp implements OpResolvable {
        private final GroupNode compileSource;
        private final GroupNode runtimeSource;

        private PhaseForwardingOp(PositionedGlyph boundary, GroupNode compileSource,
                                  GroupNode runtimeSource) {
            super("phase_forwarding", boundary);
            this.compileSource = compileSource;
            this.runtimeSource = runtimeSource;
        }

        @Override
        public OpResolution resolve(OpResolveContext context) {
            GroupNode source = context.phase() == OpResolveContext.Phase.RUNTIME
                    ? runtimeSource : compileSource;
            return new OpResolution(this, source, null, null);
        }
    }

    private static final class PersistentStub extends StubOp implements PersistentOp {
        private PersistentStub(String id, PositionedGlyph boundary) {
            super(id, boundary);
        }

        @Override
        public com.astune.gyromancy.array.runtime.RuntimeHandle activate(
                com.astune.gyromancy.array.runtime.OpRuntimeContext context) {
            return new com.astune.gyromancy.array.runtime.RuntimeHandle(Map.of());
        }

        @Override
        public void deactivate(com.astune.gyromancy.array.runtime.OpRuntimeContext context,
                               Map<String, Object> scratchData) {
        }
    }

    private static final class CapturingOp extends StubOp {
        private final List<OpInput> inputs;

        private CapturingOp(PositionedGlyph boundary, List<OpInput> inputs) {
            super("capturing", boundary);
            this.inputs = List.copyOf(inputs);
        }

        @Override
        public List<OpInput> inputs() {
            return inputs;
        }
    }
}
