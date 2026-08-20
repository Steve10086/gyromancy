package com.astune.gyromancy.compile.vector;

import com.astune.gyromancy.api.geometry.SurfaceFrame;
import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.GroupNode;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.SymbolNode;
import com.astune.gyromancy.array.compile.SequenceNode;
import com.astune.gyromancy.array.compile.VectorCompiler;
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

class VectorOpDerivationTest {
    @Test
    void derivesTheFourVectorOpsFromTheirOwnBoundaryRunes() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph loop = glyph("loop", SymbolRole.PARAMETER_RUNE, 2);
        PositionedGlyph drain = glyph("drain", SymbolRole.PARAMETER_RUNE, 3);
        PositionedGlyph engaging = glyph("engaging", SymbolRole.PARAMETER_RUNE, 4);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 5);

        VectorCompiler compiler = new VectorCompiler(VectorOpDefinitions.definitions());

        assertInstanceOf(RotationVectorOp.class, compile(compiler,
                group(circle, new SymbolNode(loop), new SymbolNode(arrow))));
        assertInstanceOf(StaticVectorOp.class, compile(compiler,
                group(circle, new SymbolNode(arrow))));
        assertInstanceOf(StaticRotationVectorOp.class, compile(compiler,
                group(circle, new SymbolNode(drain), new SymbolNode(arrow))));
        assertInstanceOf(ArrayNormalVectorOp.class, compile(compiler,
                group(circle, new SymbolNode(engaging))));
    }

    @Test
    void rotationUsesTheCurrentTickAndFallsBackToMovementForItsAxis() {
        VectorOp input = vector(ignored -> new Vec3(1.0, 0.0, 0.0));
        RotationVectorOp rotation = new RotationVectorOp(
                RotationVectorDefinition.ID, null, List.of(), 0,
                List.of(),
                List.of(new VectorComposition.Input(input, VectorComposition.Mode.DIRECT)),
                90.0);

        VectorContext firstTick = context(new Vec3(0.0, 0.0, 1.0), 1L);
        VectorContext secondTick = context(new Vec3(0.0, 0.0, 1.0), 2L);

        assertVectorEquals(new Vec3(0.0, 1.0, 0.0), rotation.provide(firstTick));
        assertVectorEquals(new Vec3(-1.0, 0.0, 0.0), rotation.provide(secondTick));
    }

    @Test
    void curlUsesMovementAsLocalYAndStaticRotationReturnsWorldAxisAngle() {
        StaticVectorOp staticVector = new StaticVectorOp(
                StaticVectorDefinition.DIRECTION_ID, null, List.of(), 0,
                List.of(new VectorComposition.Input(vector(ignored -> new Vec3(1.0, 2.0, 3.0)),
                        VectorComposition.Mode.DIRECT)), true);

        VectorContext movementAlongX = context(new Vec3(1.0, 0.0, 0.0), 0L);
        assertVectorEquals(new Vec3(2.0, 1.0, -3.0), staticVector.provide(movementAlongX));

        StaticRotationVectorOp staticRotation = new StaticRotationVectorOp(
                StaticRotationVectorDefinition.ID, null, List.of(), 0,
                List.of(new VectorComposition.Input(vector(ignored -> new Vec3(0.0, 1.0, 0.0)),
                        VectorComposition.Mode.DIRECT)), true);
        assertVectorEquals(new Vec3(0.0, 0.0, -Math.PI / 2.0),
                staticRotation.provide(movementAlongX));
    }

    @Test
    void arrayNormalUsesTheLiveFrameBeforeFallbackValues() {
        SurfaceFrame live = new SurfaceFrame(Vec3.ZERO,
                new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 1.0, 0.0),
                new Vec3(0.0, 0.0, 1.0));
        VectorContext context = new VectorContext(
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.of(live),
                Vec3.ZERO, Vec3.ZERO, new Vec3(1.0, 0.0, 0.0), 0L);

        ArrayNormalVectorOp normal = new ArrayNormalVectorOp(
                ArrayNormalVectorDefinition.ID, null, List.of(), 0, 2.0);
        assertVectorEquals(new Vec3(0.0, 0.0, 2.0), normal.provide(context));
    }

    @Test
    void persistsNestedVectorOpsWithoutExposingThemToMomentum() {
        StaticVectorOp leaf = StaticVectorOp.direct(VectorOp.RUNTIME_ID,
                new StaticVectorOp.Term(StaticVectorOp.TermKind.WORLD,
                        new Vec3(1.0, 2.0, 3.0)));
        StaticVectorOp original = new StaticVectorOp(
                StaticVectorDefinition.DIRECTION_ID, null, List.of(), 0,
                List.of(new VectorComposition.Input(leaf, VectorComposition.Mode.DIRECT)), false);

        VectorOpSerialization.SerializedVector encoded =
                VectorOpSerialization.encode(original).orElseThrow();
        StaticVectorOp loaded = assertInstanceOf(StaticVectorOp.class,
                VectorOpSerialization.decode(encoded));

        assertVectorEquals(new Vec3(1.0, 2.0, 3.0), loaded.provide(context(Vec3.ZERO, 0L)));
    }

    private static CompiledOp compile(VectorCompiler compiler, GroupNode group) {
        CompileResult<CompiledOp> result = compiler.compile(new OpInput.RawGroup(group, List.of()));
        CompileResult.Success<CompiledOp> success = assertInstanceOf(CompileResult.Success.class, result);
        return success.value();
    }

    private static VectorContext context(Vec3 velocity, long tick) {
        return new VectorContext(java.util.Optional.empty(), java.util.Optional.empty(),
                java.util.Optional.empty(), velocity, velocity, Vec3.ZERO, tick);
    }

    private static VectorOp vector(Function<VectorContext, Vec3> function) {
        return new VectorOp(VectorOp.RUNTIME_ID, null, List.of(), 0) {
            @Override
            public Vec3 provide(VectorContext context) {
                return function.apply(context);
            }
        };
    }

    private static GroupNode group(PositionedGlyph circle, com.astune.gyromancy.array.compile.ArrayNode... children) {
        return new GroupNode(circle, new SequenceNode(List.of(children)));
    }

    private static PositionedGlyph glyph(String name, SymbolRole role, int id) {
        BlockPos pos = new BlockPos(0, 64, 0);
        return new PositionedGlyph(
                UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", id)),
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
}
