package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.api.geometry.SurfaceFrame;
import com.astune.gyromancy.compile.vector.VectorContext;
import com.astune.gyromancy.compile.vector.StaticVectorOp;
import com.astune.gyromancy.compile.vector.VectorOp;
import com.astune.gyromancy.compile.vector.VectorOpDefinitions;
import com.astune.gyromancy.compile.operator.CompiledOp;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class VectorCompilerTest {
    @Test
    void compilesCircleBoundedVectorAndLeavesNestedGroupsRaw() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 2);
        PositionedGlyph nestedCircle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 3);
        PositionedGlyph nestedArrow = glyph("arrow_up", SymbolRole.PARAMETER_RUNE, 4);
        GroupNode ast = group(circle, new SymbolNode(arrow),
                group(nestedCircle, new SymbolNode(nestedArrow)));

        CompileResult<CompiledOp> result = new VectorCompiler(VectorOpDefinitions.definitions())
                .compile(new OpInput.RawGroup(ast, List.of()));

        StaticVectorOp vector = assertInstanceOf(StaticVectorOp.class,
                assertInstanceOf(CompileResult.Success.class, result).value());
        assertInstanceOf(OpInput.RawGroup.class, vector.inputs().get(1));
        assertEquals(1, vector.inputs().stream()
                .filter(OpInput.RawGroup.class::isInstance)
                .count());
    }

    @Test
    void rejectsNonCircleRootWithoutTryingAnotherBoundaryPolicy() {
        PositionedGlyph center = glyph("fire", SymbolRole.CENTER_SYMBOL, 1);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 2);
        GroupNode ast = group(center, new SymbolNode(arrow));

        CompileResult<CompiledOp> result = new VectorCompiler(VectorOpDefinitions.definitions())
                .compile(new OpInput.RawGroup(ast, List.of()));

        CompileResult.Failure<CompiledOp> failure =
                assertInstanceOf(CompileResult.Failure.class, result);
        assertEquals("invalid_vector_boundary", failure.diagnostics().getFirst().code());
    }

    @Test
    void canRecompileAGroupThatWasAlreadyCompiledByTheGlobalCompiler() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 2);
        GroupNode ast = group(circle, new SymbolNode(arrow));
        CompileResult<CompiledArray> compiledResult = ArrayNodeCompiler.compile(
                ast, VectorOpDefinitions.definitions());
        CompileResult.Success<CompiledArray> compiledSuccess =
                assertInstanceOf(CompileResult.Success.class, compiledResult);
        CompiledArray compiled = compiledSuccess.value();

        CompileResult<CompiledOp> result = new VectorCompiler(VectorOpDefinitions.definitions())
                .compile(new OpInput.Op(compiled.root(), ast));

        assertInstanceOf(StaticVectorOp.class,
                assertInstanceOf(CompileResult.Success.class, result).value());
    }

    @Test
    void matchesExactlyTheDefinitionsSuppliedByTheInstance() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 2);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("gyromancy", "non_vector_test");
        OpDefinition definition = new OpDefinition() {
            @Override
            public ResourceLocation id() {
                return id;
            }

            @Override
            public List<OpInputMatcher> match() {
                return List.of(OpInputMatcher.rune("arrow"));
            }

            @Override
            public CompileResult<CompiledOp> compile(PositionedGlyph boundary,
                                                     List<OpInput> matchedInputs,
                                                     List<OpInput> inputs) {
                return new CompileResult.Success<>(new CompiledOp() {
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
                        return inputs;
                    }

                    @Override
                    public int color() {
                        return 0;
                    }
                });
            }
        };

        CompileResult<CompiledOp> result = new VectorCompiler(List.of(definition))
                .compile(new OpInput.RawGroup(group(circle, new SymbolNode(arrow)), List.of()));

        CompileResult.Success<CompiledOp> success = assertInstanceOf(CompileResult.Success.class, result);
        assertEquals(id, success.value().id());
    }

    @Test
    void vectorOpsResolveTheirOwnFramePolicyFromSharedContext() {
        SurfaceFrame compile = new SurfaceFrame(Vec3.ZERO,
                new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1));
        SurfaceFrame activation = new SurfaceFrame(Vec3.ZERO,
                new Vec3(0, 1, 0), new Vec3(0, 0, 1), new Vec3(1, 0, 0));
        SurfaceFrame live = new SurfaceFrame(Vec3.ZERO,
                new Vec3(1, 0, 0), new Vec3(0, 0, 1), new Vec3(0, 1, 0));
        VectorContext context = new VectorContext(compile, activation, live,
                Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
        Vec3 local = new Vec3(0, 0, 2);

        assertVectorEquals(local, vector(ignored -> local).provide(context));
        assertVectorEquals(new Vec3(2, 0, 0), vector(ignored ->
                context.activationFrame().map(frame -> frame.normal().scale(local.z)).orElse(Vec3.ZERO))
                .provide(context));
        assertVectorEquals(new Vec3(0, 2, 0), vector(ignored ->
                context.liveFrame().map(frame -> frame.normal().scale(local.z)).orElse(Vec3.ZERO))
                .provide(context));
    }

    private static GroupNode group(PositionedGlyph circle, ArrayNode... children) {
        return new GroupNode(circle, new SequenceNode(List.of(children)));
    }

    private static PositionedGlyph glyph(String name, SymbolRole role, int id) {
        BlockPos pos = new BlockPos(0, 64, 0);
        return new PositionedGlyph(
                UUID.fromString("00000000-0000-0000-0000-00000000000" + id),
                id,
                ResourceLocation.fromNamespaceAndPath("gyromancy", name),
                0.9F,
                role,
                new Vec3(1.0, 0.0, 0.0),
                2.0,
                1.0,
                pos,
                0.0, 4.0, 0.0, 4.0,
                Set.of(new PixelPos(pos, Direction.NORTH, id, id, 0xFF00AA00)));
    }

    private static void assertVectorEquals(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 1.0E-9);
        assertEquals(expected.y, actual.y, 1.0E-9);
        assertEquals(expected.z, actual.z, 1.0E-9);
    }

    private static VectorOp vector(Function<VectorContext, Vec3> function) {
        return new VectorOp(VectorOp.RUNTIME_ID, null, List.of(), 0) {
            @Override
            protected Vec3 provideVector(VectorContext context) {
                return function.apply(context);
            }
        };
    }
}
