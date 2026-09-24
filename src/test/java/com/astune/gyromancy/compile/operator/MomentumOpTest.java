package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.geometry.SurfaceFrame;
import com.astune.gyromancy.array.compile.ArrayNode;
import com.astune.gyromancy.array.compile.ArrayNodeCompiler;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.CompiledArray;
import com.astune.gyromancy.array.compile.GroupNode;
import com.astune.gyromancy.array.compile.LocalCompiler;
import com.astune.gyromancy.array.compile.RuntimeModel;
import com.astune.gyromancy.array.compile.SequenceNode;
import com.astune.gyromancy.array.compile.SymbolNode;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import com.astune.gyromancy.compile.vector.VectorContext;
import com.astune.gyromancy.compile.vector.VectorOp;
import com.astune.gyromancy.compile.vector.StaticVectorOp;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class MomentumOpTest {
    private static final double EPSILON = 1.0E-9;

    @Test
    void solvesPlanarArrowAndForwardArrowAgainstEntityFacing() {
        MomentumOp op = new MomentumOp(List.of(
                staticInput(new Vec3(1.0, 0.0, 1.0), 2.0, false),
                staticInput(Vec3.ZERO, 3.0, true)));

        Vec3 acceleration = op.accelerationForTick(new Vec3(0.0, 0.0, 4.0));

        assertVectorEquals(new Vec3(0.2, 0.0, 0.3), acceleration);
    }

    @Test
    void replacesHistoricalAccelerationWhenEntityRotates() {
        MomentumOp op = new MomentumOp(List.of(
                staticInput(new Vec3(1.0, 0.0, 0.0), 2.0, false),
                dynamicFacingInput(3.0)));

        Vec3 first = op.accelerationForTick(new Vec3(0.0, 0.0, 1.0));
        Vec3 afterRotation = op.accelerationForTick(new Vec3(0.0, 1.0, 0.0));

        assertVectorEquals(new Vec3(0.2, 0.0, 0.3), first);
        assertVectorEquals(new Vec3(0.2, 0.3, 0.0), afterRotation);
    }

    @Test
    void directMotionModeDoesNotProjectAwayAParallelDirection() {
        MomentumOp op = new MomentumOp(List.of(
                staticInput(new Vec3(2.0, 0.0, 0.0), MomentumOp.MotionMode.DIRECT)));

        assertVectorEquals(new Vec3(2.0, 0.0, 0.0),
                op.solveVector(new Vec3(1.0, 0.0, 0.0)));
    }

    @Test
    void tickPhaseStartsAtTickZeroAndSpawnPhaseOnlyModifiesEmission() {
        MomentumOp.AccelerationInput input = staticInput(
                new Vec3(2.0, 0.0, 0.0), MomentumOp.MotionMode.DIRECT);
        MomentumOp tick = new MomentumOp(List.of(input), MomentumOp.ApplicationPhase.TICK);
        MomentumOp spawn = new MomentumOp(List.of(input), MomentumOp.ApplicationPhase.SPAWN);

        assertVectorEquals(new Vec3(0.2, 0.0, 0.0),
                tick.accelerationForTick(new Vec3(0.0, 0.0, 1.0)));
        assertEquals(1, tick.elapsedTicks());
        assertVectorEquals(Vec3.ZERO,
                tick.modifyEntityEmission(new EmitOp.Emission(
                        Vec3.ZERO, 0.0, 1.0F, false), OpRuntimeContext.empty()).velocity());
        assertVectorEquals(new Vec3(2.0, 0.0, 0.0),
                spawn.modifyEntityEmission(new EmitOp.Emission(
                        Vec3.ZERO, 0.0, 1.0F, false), OpRuntimeContext.empty()).velocity());
    }

    @Test
    void directChildContributionAddsMomentumToInitialVelocity() {
        MomentumOp op = new MomentumOp(List.of(
                staticInput(new Vec3(1.0, 0.0, 0.0), 2.0, false),
                staticInput(Vec3.ZERO, 3.0, true)), MomentumOp.ApplicationPhase.SPAWN);
        EmitOp.Emission original = new EmitOp.Emission(
                new Vec3(0.0, 0.0, 4.0), 4.0, 0.5F, true);

        EmitOp.Emission modified = op.modifyEntityEmission(original, OpRuntimeContext.empty());

        assertVectorEquals(new Vec3(2.0, 0.0, 7.0), modified.velocity());
        assertEquals(9.0, modified.motionSum(), EPSILON);
        assertEquals(0.5F, modified.sizeScale());
        assertEquals(true, modified.hasMotion());
        assertEquals(0, op.elapsedTicks());
    }

    @Test
    void projectileAppliesDirectMomentumWhenResolvingSpawnEmission() {
        PositionedGlyph outer = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph inner = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 2);
        PositionedGlyph fire = glyph("fire", SymbolRole.CENTER_SYMBOL, 3);
        PositionedGlyph motion = glyph("motion", SymbolRole.PARAMETER_RUNE, 4);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 5);
        GroupNode ast = group(outer, new SymbolNode(fire),
                group(inner, new SymbolNode(motion), new SymbolNode(arrow)));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));
        FireProjectileOp projectile = assertInstanceOf(FireProjectileOp.class,
                materializedRoot(success.value()));

        EmitOp.Emission emission = projectile.emissions().getFirst();

        assertVectorEquals(new Vec3(2.0, 0.0, 0.0), emission.velocity());
        assertEquals(2.0, emission.motionSum(), EPSILON);
        assertEquals(true, emission.hasMotion());
    }

    @Test
    void codecPreservesCurrentAccelerationAndElapsedTicks() {
        MomentumOp op = new MomentumOp(List.of(
                staticInput(new Vec3(1.0, 0.0, 0.0), 2.0, false)));
        op.accelerationForTick(new Vec3(0.0, 0.0, 1.0));
        op.accelerationForTick(new Vec3(0.0, 0.0, 1.0));

        CompoundTag encoded = op.savePayload();
        MomentumOp loaded = (MomentumOp) EntityPayload.loadPayload(encoded).orElseThrow();

        assertEquals(2, loaded.elapsedTicks());
        assertVectorEquals(new Vec3(2.0, 0.0, 0.0), loaded.currentAcceleration());
        assertVectorEquals(new Vec3(0.2, 0.0, 0.0),
                loaded.accelerationForTick(new Vec3(0.0, 1.0, 0.0)));
    }

    @Test
    void legacyAlongFacingPayloadWithoutFrameUsesVelocityVectorOp() {
        CompoundTag input = new CompoundTag();
        CompoundTag direction = new CompoundTag();
        direction.putDouble("x", 0.0);
        direction.putDouble("y", 0.0);
        direction.putDouble("z", 0.0);
        input.put("direction", direction);
        input.putDouble("magnitude", 3.0);
        input.putBoolean("along_facing", true);

        ListTag inputs = new ListTag();
        inputs.add(input);
        CompoundTag payload = new CompoundTag();
        payload.put("inputs", inputs);

        MomentumOp loaded = MomentumOp.CODEC.parse(NbtOps.INSTANCE, payload)
                .result().orElseThrow();

        assertVectorEquals(new Vec3(0.0, 0.0, 0.3),
                loaded.accelerationForTick(new Vec3(0.0, 0.0, 1.0),
                        new Vec3(1.0, 0.0, 0.0)));
    }

    @Test
    void resolvesCompileLocalDirectionAgainstLiveArrayFrameWithoutRefreshingGlyphSnapshot() {
        PositionedGlyph outer = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        PositionedGlyph motion = glyph("motion", SymbolRole.PARAMETER_RUNE, 2);
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 3);
        GroupNode ast = group(outer, new SymbolNode(motion), new SymbolNode(arrow));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));
        MomentumOp op = assertInstanceOf(MomentumOp.class, materializedRoot(success.value()));
        @SuppressWarnings("unchecked")
        var liveSuccess = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));
        MomentumOp liveOp = assertInstanceOf(MomentumOp.class, materializedRoot(liveSuccess.value()));

        SurfaceFrame liveFrame = SurfaceFrame.facing(
                Vec3.ZERO, new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 1.0, 0.0));
        PositionedGlyph liveOuter = withSurface(outer, liveFrame);
        PositionedGlyph liveArrow = withSurface(
                new PositionedGlyph(
                        arrow.glyphUuid(), arrow.glyphId(), arrow.symbolId(), arrow.confidence(), arrow.role(),
                        new Vec3(0.0, 1.0, 0.0), 9.0, arrow.width(), arrow.worldPos(),
                        arrow.minWorldX(), arrow.maxWorldX(), arrow.minWorldY(), arrow.maxWorldY(),
                        arrow.pixels(), arrow.sourceCanvasId(), liveFrame), liveFrame);
        ArrayObject liveArray = new ArrayObject(
                UUID.randomUUID(), liveOuter, List.of(liveOuter, liveArrow), java.util.Map.of());
        OpRuntimeContext context = new OpRuntimeContext(null, op).withArray(liveArray, outer);

        EmitOp.Emission snapshotEmission = op.modifyEntityEmission(
                new EmitOp.Emission(Vec3.ZERO, 0.0, 1.0F, false), OpRuntimeContext.empty());
        EmitOp.Emission liveFrameEmission = liveOp.modifyEntityEmission(
                new EmitOp.Emission(Vec3.ZERO, 0.0, 1.0F, false), context);

        assertVectorEquals(new Vec3(2.0, 0.0, 0.0), snapshotEmission.velocity());
        assertVectorEquals(new Vec3(0.0, 0.0, 2.0), liveFrameEmission.velocity());
        assertEquals(MomentumOp.MotionMode.TANGENTIAL,
                op.velocityInputs().getFirst().motionMode());
    }

    @Test
    void liveArrayStrategyRemainsLiveUntilVectorIsConsumed() {
        PositionedGlyph outer = glyph("circle_outer", SymbolRole.OUTER_CIRCLE, 1);
        SurfaceFrame liveFrame = SurfaceFrame.facing(
                Vec3.ZERO, new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 1.0, 0.0));
        PositionedGlyph liveOuter = withSurface(outer, liveFrame);
        ArrayObject liveArray = new ArrayObject(
                UUID.randomUUID(), liveOuter, List.of(liveOuter), java.util.Map.of());
        MomentumOp op = new MomentumOp(List.of(new MomentumOp.AccelerationInput(
                liveVector(new Vec3(0.0, 0.0, 2.0)), MomentumOp.MotionMode.DIRECT)),
                MomentumOp.ApplicationPhase.SPAWN);

        EmitOp.Emission emission = op.modifyEntityEmission(
                new EmitOp.Emission(Vec3.ZERO, 0.0, 1.0F, false),
                new OpRuntimeContext(null, op).withArray(liveArray, outer));

        assertVectorEquals(new Vec3(2.0, 0.0, 0.0), emission.velocity());
    }

    @Test
    void spawnModeSnapshotsAnUnknownVectorOpOnlyOnce() {
        CountingVectorOp vector = new CountingVectorOp();
        MomentumOp op = new MomentumOp(List.of(new MomentumOp.AccelerationInput(
                vector, MomentumOp.MotionMode.DIRECT)), MomentumOp.ApplicationPhase.SPAWN);

        EmitOp.Emission first = op.modifyEntityEmission(
                new EmitOp.Emission(Vec3.ZERO, 0.0, 1.0F, false), OpRuntimeContext.empty());
        EmitOp.Emission second = op.modifyEntityEmission(
                new EmitOp.Emission(Vec3.ZERO, 0.0, 1.0F, false), OpRuntimeContext.empty());

        assertEquals(1, vector.calls);
        assertVectorEquals(new Vec3(1.0, 0.0, 0.0), first.velocity());
        assertVectorEquals(first.velocity(), second.velocity());
    }

    @Test
    void tickModeSamplesAnUnknownVectorOpForEveryTick() {
        CountingVectorOp vector = new CountingVectorOp();
        MomentumOp op = new MomentumOp(List.of(new MomentumOp.AccelerationInput(
                vector, MomentumOp.MotionMode.DIRECT, MomentumOp.UpdateMode.DYNAMIC)),
                MomentumOp.ApplicationPhase.TICK);

        Vec3 first = op.accelerationForTick(Vec3.ZERO);
        Vec3 second = op.accelerationForTick(Vec3.ZERO);

        assertEquals(2, vector.calls);
        assertVectorEquals(new Vec3(0.1, 0.0, 0.0), first);
        assertVectorEquals(new Vec3(0.0, 0.1, 0.0), second);
    }

    @Test
    void snapshotAccelerationSamplesAnUnknownVectorOpOnce() {
        CountingVectorOp vector = new CountingVectorOp();
        MomentumOp op = new MomentumOp(List.of(new MomentumOp.AccelerationInput(
                vector, MomentumOp.MotionMode.DIRECT, MomentumOp.UpdateMode.SNAPSHOT)),
                MomentumOp.ApplicationPhase.TICK);

        Vec3 first = op.accelerationForTick(Vec3.ZERO);
        Vec3 second = op.accelerationForTick(Vec3.ZERO);

        assertEquals(1, vector.calls);
        assertVectorEquals(new Vec3(0.1, 0.0, 0.0), first);
        assertVectorEquals(first, second);
    }

    private static CompiledOp materializedRoot(CompiledArray compiled) {
        CompileResult<RuntimeModel> result = new LocalCompiler().compile(compiled);
        CompileResult.Success<RuntimeModel> success =
                assertInstanceOf(CompileResult.Success.class, result);
        return success.value().root();
    }

    private static void assertVectorEquals(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }

    private static MomentumOp.AccelerationInput staticInput(Vec3 direction,
                                                            double magnitude,
                                                            boolean alongFacing) {
        return new MomentumOp.AccelerationInput(
                alongFacing
                        ? facingVector(magnitude)
                        : fixedVector(scaled(direction, magnitude)),
                alongFacing ? MomentumOp.MotionMode.DIRECT : MomentumOp.MotionMode.TANGENTIAL);
    }

    private static MomentumOp.AccelerationInput staticInput(Vec3 vector,
                                                            MomentumOp.MotionMode mode) {
        return new MomentumOp.AccelerationInput(fixedVector(vector), mode);
    }

    private static MomentumOp.AccelerationInput dynamicFacingInput(double magnitude) {
        return new MomentumOp.AccelerationInput(facingVector(magnitude),
                MomentumOp.MotionMode.DIRECT, MomentumOp.UpdateMode.DYNAMIC);
    }

    private static VectorOp fixedVector(Vec3 vector) {
        return StaticVectorOp.literal(vector);
    }

    private static VectorOp facingVector(double magnitude) {
        return vector(context -> context.facing().lengthSqr() < EPSILON
                ? Vec3.ZERO : context.facing().normalize().scale(magnitude));
    }

    private static VectorOp liveVector(Vec3 local) {
        return vector(context -> context.liveFrame()
                .or(() -> context.activationFrame())
                .or(() -> context.compileFrame())
                .map(frame -> frame.axisU().scale(local.x)
                        .add(frame.axisV().scale(local.y))
                        .add(frame.normal().scale(local.z)))
                .orElse(local));
    }

    private static VectorOp vector(Function<VectorContext, Vec3> function) {
        return new VectorOp(VectorOp.RUNTIME_ID, null, List.of(), 0) {
            @Override
            protected Vec3 provideVector(VectorContext context) {
                return function.apply(context);
            }
        };
    }

    private static Vec3 scaled(Vec3 vector, double magnitude) {
        return vector.lengthSqr() < EPSILON ? Vec3.ZERO : vector.normalize().scale(magnitude);
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

    private static PositionedGlyph withSurface(PositionedGlyph glyph, SurfaceFrame surface) {
        return new PositionedGlyph(
                glyph.glyphUuid(), glyph.glyphId(), glyph.symbolId(), glyph.confidence(), glyph.role(),
                glyph.front(), glyph.length(), glyph.width(), glyph.worldPos(),
                glyph.minWorldX(), glyph.maxWorldX(), glyph.minWorldY(), glyph.maxWorldY(),
                glyph.pixels(), glyph.sourceCanvasId(), surface);
    }

    private static final class CountingVectorOp extends VectorOp {
        private int calls;

        private CountingVectorOp() {
            super(ResourceLocation.fromNamespaceAndPath("gyromancy", "counting_vector"),
                    null, List.of(), 0);
        }

        @Override
        protected Vec3 provideVector(VectorContext context) {
            calls++;
            return calls == 1 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        }
    }
}
