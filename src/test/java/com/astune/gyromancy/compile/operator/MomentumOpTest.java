package com.astune.gyromancy.compile.operator;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
