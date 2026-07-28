package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.compile.ArrayNode;
import com.astune.gyromancy.array.compile.ArrayNodeCompiler;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.CompiledArray;
import com.astune.gyromancy.array.compile.GroupNode;
import com.astune.gyromancy.array.compile.SequenceNode;
import com.astune.gyromancy.array.compile.SymbolNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class MomentumOpTest {
    private static final double EPSILON = 1.0E-9;

    @Test
    void solvesPlanarArrowAndForwardArrowAgainstEntityFacing() {
        MomentumOp op = new MomentumOp(List.of(
                new MomentumOp.AccelerationInput(new Vec3(1.0, 0.0, 1.0), 2.0, false),
                new MomentumOp.AccelerationInput(Vec3.ZERO, 3.0, true)));

        Vec3 acceleration = op.accelerationForTick(new Vec3(0.0, 0.0, 4.0));

        assertVectorEquals(new Vec3(2.0, 0.0, 3.0), acceleration);
    }

    @Test
    void replacesHistoricalAccelerationWhenEntityRotates() {
        MomentumOp op = new MomentumOp(List.of(
                new MomentumOp.AccelerationInput(new Vec3(1.0, 0.0, 0.0), 2.0, false),
                new MomentumOp.AccelerationInput(Vec3.ZERO, 3.0, true)));

        Vec3 first = op.accelerationForTick(new Vec3(0.0, 0.0, 1.0));
        Vec3 afterRotation = op.accelerationForTick(new Vec3(0.0, 1.0, 0.0));

        assertVectorEquals(new Vec3(2.0, 0.0, 3.0), first);
        assertVectorEquals(new Vec3(2.0, 3.0, 0.0), afterRotation);
    }

    @Test
    void directChildContributionAddsMomentumToInitialVelocity() {
        MomentumOp op = new MomentumOp(List.of(
                new MomentumOp.AccelerationInput(new Vec3(1.0, 0.0, 0.0), 2.0, false),
                new MomentumOp.AccelerationInput(Vec3.ZERO, 3.0, true)));
        EmitOp.Emission original = new EmitOp.Emission(
                new Vec3(0.0, 0.0, 4.0), 4.0, 0.5F, true);

        EmitOp.Emission modified = op.modifyEntityEmission(original);

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
        PositionedGlyph arrow = glyph("arrow", SymbolRole.PARAMETER_RUNE, 4);
        GroupNode ast = group(outer, new SymbolNode(fire),
                group(inner, new SymbolNode(arrow)));

        @SuppressWarnings("unchecked")
        var success = (CompileResult.Success<CompiledArray>) assertInstanceOf(CompileResult.Success.class,
                ArrayNodeCompiler.compile(ast));
        FireProjectileOp projectile = assertInstanceOf(FireProjectileOp.class, success.value().root());

        EmitOp.Emission emission = projectile.emissions().getFirst();

        assertVectorEquals(new Vec3(0.2, 0.0, 0.0), emission.velocity());
        assertEquals(0.2, emission.motionSum(), EPSILON);
        assertEquals(true, emission.hasMotion());
    }

    @Test
    void appliesAccelerationForExactlyOneHundredTicksThenClearsIt() {
        MomentumOp op = new MomentumOp(List.of(
                new MomentumOp.AccelerationInput(new Vec3(1.0, 0.0, 0.0), 1.0, false)));

        for (int tick = 0; tick < MomentumOp.ACTIVE_TICKS; tick++) {
            assertVectorEquals(new Vec3(1.0, 0.0, 0.0),
                    op.accelerationForTick(new Vec3(0.0, 0.0, 1.0)));
        }

        assertEquals(MomentumOp.ACTIVE_TICKS, op.elapsedTicks());
        assertVectorEquals(Vec3.ZERO, op.currentAcceleration());
        assertVectorEquals(Vec3.ZERO, op.accelerationForTick(new Vec3(0.0, 0.0, 1.0)));
    }

    @Test
    void codecPreservesCurrentAccelerationAndElapsedTicks() {
        MomentumOp op = new MomentumOp(List.of(
                new MomentumOp.AccelerationInput(new Vec3(1.0, 0.0, 0.0), 2.0, false)));
        op.accelerationForTick(new Vec3(0.0, 0.0, 1.0));
        op.accelerationForTick(new Vec3(0.0, 0.0, 1.0));

        CompoundTag encoded = op.savePayload();
        MomentumOp loaded = (MomentumOp) EntityPayload.loadPayload(encoded).orElseThrow();

        assertEquals(2, loaded.elapsedTicks());
        assertVectorEquals(new Vec3(2.0, 0.0, 0.0), loaded.currentAcceleration());
        assertVectorEquals(new Vec3(2.0, 0.0, 0.0),
                loaded.accelerationForTick(new Vec3(0.0, 1.0, 0.0)));
    }

    private static void assertVectorEquals(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
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
}
