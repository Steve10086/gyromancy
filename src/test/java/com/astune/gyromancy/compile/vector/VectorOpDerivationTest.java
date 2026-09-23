package com.astune.gyromancy.compile.vector;

import com.astune.gyromancy.api.geometry.SurfaceFrame;
import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.compile.ArrayNode;
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

import java.util.ArrayList;
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
        PositionedGlyph space = glyph("space", SymbolRole.CENTER_SYMBOL, 6);
        PositionedGlyph revert = glyph("revert", SymbolRole.PARAMETER_RUNE, 7);

        VectorCompiler compiler = new VectorCompiler(VectorOpDefinitions.definitions());

        assertInstanceOf(RotationVectorOp.class, compile(compiler,
                group(circle, new SymbolNode(loop), new SymbolNode(arrow))));
        assertInstanceOf(StaticVectorOp.class, compile(compiler,
                group(circle, new SymbolNode(arrow))));
        assertInstanceOf(StaticRotationVectorOp.class, compile(compiler,
                group(circle, new SymbolNode(drain), new SymbolNode(arrow))));
        assertInstanceOf(ArrayNormalVectorOp.class, compile(compiler,
                group(circle, new SymbolNode(engaging))));
        assertInstanceOf(GravityVectorOp.class, compile(compiler,
                group(circle, new SymbolNode(space))));
        assertInstanceOf(StaticVectorOp.class, compile(compiler,
                group(circle, new SymbolNode(revert), new SymbolNode(arrow))));
    }

    @Test
    void gravityProvidesTheOwningEntityGravityAcceleration() {
        GravityVectorOp gravity = new GravityVectorOp(
                GravityVectorDefinition.ID, null, List.of(), 0);
        VectorContext context = new VectorContext(null, null, null,
                Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 0L, 0.08);

        assertVectorEquals(new Vec3(0.0, -0.08, 0.0), gravity.provide(context));
    }

    @Test
    void speedVectorIsBoundToTheEngagingCurlCombination() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph engaging = glyph("engaging", SymbolRole.PARAMETER_RUNE, 2);
        PositionedGlyph curl = glyph("curl", SymbolRole.PARAMETER_RUNE, 3);
        VectorCompiler compiler = new VectorCompiler(VectorOpDefinitions.definitions());

        VectorOp speed = assertInstanceOf(SpeedOp.class, compile(compiler,
                group(circle, new SymbolNode(engaging), new SymbolNode(curl))));

        assertVectorEquals(new Vec3(0.25, -0.5, 0.75),
                speed.provide(context(new Vec3(0.25, -0.5, 0.75), 0L)));
    }

    @Test
    void speedVectorPersistsThroughItsAdapter() {
        SpeedOp speed = new SpeedOp(SpeedVectorDefinition.ID, null, List.of(), 0);

        VectorOp loaded = VectorOpSerialization.decode(
                VectorOpSerialization.encode(speed).orElseThrow());

        assertVectorEquals(new Vec3(0.5, 0.0, -0.25),
                loaded.provide(context(new Vec3(0.5, 0.0, -0.25), 0L)));
    }

    @Test
    void rawGroupMatchesStaticVectorWithoutAddingAVector() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph inner = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 2);
        PositionedGlyph space = glyph("space", SymbolRole.CENTER_SYMBOL, 3);
        VectorCompiler compiler = new VectorCompiler(VectorOpDefinitions.definitions());

        VectorOp groupVector = assertInstanceOf(StaticVectorOp.class, compile(compiler,
                group(circle, group(inner, new SymbolNode(space)))));
        VectorContext context = new VectorContext(null, null, null,
                Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 0L, 0.08);

        assertVectorEquals(new Vec3(0.0, -0.08, 0.0), groupVector.provide(context));
    }

    @Test
    void secretTextsDecodeToConsecutiveIntegerScales() {
        VectorCompiler compiler = new VectorCompiler(VectorOpDefinitions.definitions());
        VectorContext context = new VectorContext(null, null, null,
                Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 0L, 0.08);

        assertVectorEquals(new Vec3(0.0, -0.08, 0.0),
                scaledSpace(compiler, context, 1, "secret_text_2"));
        assertVectorEquals(new Vec3(0.0, -0.16, 0.0),
                scaledSpace(compiler, context, 4, "secret_text_3"));
        assertVectorEquals(new Vec3(0.0, -0.24, 0.0),
                scaledSpace(compiler, context, 7, "secret_text_2", "secret_text_3", "secret_text_3"));
        assertVectorEquals(new Vec3(0.0, -0.32, 0.0),
                scaledSpace(compiler, context, 11, "secret_text_4"));
        assertVectorEquals(new Vec3(0.0, -0.04, 0.0),
                scaledSpace(compiler, context, 14, "secret_text_1", "secret_text_3"));
        assertVectorEquals(new Vec3(0.0, -0.016, 0.0),
                scaledSpace(compiler, context, 17, "secret_text_1", "secret_text_2", "secret_text_4"));
        assertVectorEquals(new Vec3(0.0, -0.08, 0.0),
                scaledSpace(compiler, context, 21, "secret_text_1"));
    }

    private static Vec3 scaledSpace(VectorCompiler compiler, VectorContext context,
                                    int baseId, String... secrets) {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, baseId);
        PositionedGlyph inner = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, baseId + 1);
        List<ArrayNode> children = new ArrayList<>();
        for (int index = 0; index < secrets.length; index++) {
            children.add(new SymbolNode(glyph(secrets[index], SymbolRole.PARAMETER_RUNE,
                    baseId + 2 + index)));
        }
        children.add(group(inner, new SymbolNode(glyph("space", SymbolRole.CENTER_SYMBOL,
                baseId + 2 + secrets.length))));

        VectorOp op = assertInstanceOf(StaticVectorOp.class,
                compile(compiler, group(circle, children.toArray(new ArrayNode[0]))));
        return op.provide(context);
    }

    @Test
    void gravityRejectsAdditionalVectorRunes() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph space = glyph("space", SymbolRole.CENTER_SYMBOL, 2);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 3);
        VectorCompiler compiler = new VectorCompiler(VectorOpDefinitions.definitions());

        CompileResult<CompiledOp> result = compiler.compile(new OpInput.RawGroup(
                group(circle, new SymbolNode(space), new SymbolNode(arrow)), List.of()));

        assertInstanceOf(CompileResult.Failure.class, result);
    }

    @Test
    void revertNegatesTheVectorItIsAttachedTo() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph revert = glyph("revert", SymbolRole.PARAMETER_RUNE, 2);
        PositionedGlyph inner = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 3);
        PositionedGlyph space = glyph("space", SymbolRole.CENTER_SYMBOL, 4);
        VectorCompiler compiler = new VectorCompiler(VectorOpDefinitions.definitions());
        VectorContext context = new VectorContext(null, null, null,
                Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 0L, 0.08);

        VectorOp wrapped = assertInstanceOf(StaticVectorOp.class, compile(compiler,
                group(circle, new SymbolNode(revert), group(inner, new SymbolNode(space)))));
        assertVectorEquals(new Vec3(0.0, 0.08, 0.0), wrapped.provide(context));

        GravityVectorOp direct = new GravityVectorOp(
                GravityVectorDefinition.ID, null, List.of(), 0, 1.0, true);
        assertVectorEquals(new Vec3(0.0, 0.08, 0.0), direct.provide(context));
    }

    @Test
    void modifiersApplyToLeafVectorOps() {
        PositionedGlyph circle = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph space = glyph("space", SymbolRole.CENTER_SYMBOL, 2);
        PositionedGlyph revert = glyph("revert", SymbolRole.PARAMETER_RUNE, 3);
        PositionedGlyph secret = glyph("secret_text_3", SymbolRole.PARAMETER_RUNE, 4);
        VectorCompiler compiler = new VectorCompiler(VectorOpDefinitions.definitions());
        VectorContext context = new VectorContext(null, null, null,
                Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 0L, 0.08);

        VectorOp gravity = assertInstanceOf(GravityVectorOp.class, compile(compiler,
                group(circle, new SymbolNode(space), new SymbolNode(revert), new SymbolNode(secret))));

        assertVectorEquals(new Vec3(0.0, 0.16, 0.0), gravity.provide(context));
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
    void rotationInterpretsRevertAsAReversedRotation() {
        VectorOp input = vector(ignored -> new Vec3(1.0, 0.0, 0.0));
        RotationVectorOp forward = new RotationVectorOp(
                RotationVectorDefinition.ID, null, List.of(), 0,
                List.of(),
                List.of(new VectorComposition.Input(input, VectorComposition.Mode.DIRECT)),
                60.0);
        RotationVectorOp reversed = new RotationVectorOp(
                RotationVectorDefinition.ID, null, List.of(), 0,
                List.of(),
                List.of(new VectorComposition.Input(input, VectorComposition.Mode.DIRECT)),
                60.0, 1.0, true);
        VectorContext tick = context(new Vec3(0.0, 0.0, 1.0), 1L);

        assertVectorEquals(new Vec3(0.5, Math.sqrt(3.0) / 2.0, 0.0), forward.provide(tick));
        assertVectorEquals(new Vec3(0.5, -Math.sqrt(3.0) / 2.0, 0.0), reversed.provide(tick));
    }

    @Test
    void staticRotationInterpretsRevertAsAReversedTarget() {
        VectorOp input = vector(ignored -> new Vec3(0.0, 1.0, 1.0));
        StaticRotationVectorOp forward = new StaticRotationVectorOp(
                StaticRotationVectorDefinition.ID, null, List.of(), 0,
                List.of(new VectorComposition.Input(input, VectorComposition.Mode.DIRECT)), false);
        StaticRotationVectorOp reversed = new StaticRotationVectorOp(
                StaticRotationVectorDefinition.ID, null, List.of(), 0,
                List.of(new VectorComposition.Input(input, VectorComposition.Mode.DIRECT)), false,
                1.0, true);
        VectorContext context = context(Vec3.ZERO, 0L);

        assertVectorEquals(new Vec3(Math.PI / 4.0, 0.0, 0.0), forward.provide(context));
        assertVectorEquals(new Vec3(-3.0 * Math.PI / 4.0, 0.0, 0.0), reversed.provide(context));
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
    void compositionSumsOpposingVectorsWithoutRestoringTheirLength() {
        VectorOp right = StaticVectorOp.literal(new Vec3(2.0, 0.0, 0.0));
        VectorOp left = StaticVectorOp.literal(new Vec3(-2.0, 0.0, 0.0));
        VectorContext context = new VectorContext(null, null, null,
                Vec3.ZERO, Vec3.ZERO, new Vec3(0.0, 1.0, 0.0), 0L, 0.0);

        Vec3 composed = VectorComposition.compose(context, List.of(
                new VectorComposition.Input(right, VectorComposition.Mode.DIRECT),
                new VectorComposition.Input(left, VectorComposition.Mode.DIRECT)));

        assertVectorEquals(Vec3.ZERO, composed);
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
            protected Vec3 provideVector(VectorContext context) {
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
