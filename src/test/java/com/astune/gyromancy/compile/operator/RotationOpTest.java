package com.astune.gyromancy.compile.operator;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotationOpTest {
    private static final double EPSILON = 1.0E-9;

    @Test
    void appliesConfiguredRotationSpeedForExactlyOneHundredTicks() {
        RotationOp op = new RotationOp(2.5);

        for (int tick = 0; tick < RotationOp.ACTIVE_TICKS; tick++) {
            assertEquals(2.5, op.rotationForTick(), EPSILON);
        }

        assertEquals(RotationOp.ACTIVE_TICKS, op.elapsedTicks());
        assertEquals(0.0, op.rotationForTick(), EPSILON);
    }

    @Test
    void negativeSpeedRotatesInReverse() {
        RotationOp op = new RotationOp(-3.0);

        assertEquals(-3.0, op.rotationForTick(), EPSILON);
    }

    @Test
    void rotatesAroundEntityLocalUpInsteadOfWorldY() {
        double diagonal = Math.sqrt(0.5);
        Vec3 pitchedForward = new Vec3(0.0, -diagonal, diagonal);

        Vec3 rotated = RotationOp.rotatedFacing(pitchedForward, Vec3.ZERO, 90.0);

        assertVectorEquals(new Vec3(-1.0, 0.0, 0.0), rotated);
    }

    @Test
    void establishesLocalFrameFromVelocityWhenEntityFacingIsUnavailable() {
        Vec3 rotated = RotationOp.rotatedFacing(Vec3.ZERO, new Vec3(0.0, 0.0, 4.0), 90.0);

        assertVectorEquals(new Vec3(-1.0, 0.0, 0.0), rotated);
    }

    @Test
    void firstTickUsesVelocityInsteadOfDefaultEntityFacing() {
        Vec3 rotated = RotationOp.rotatedFacingForTick(
                new Vec3(0.0, 0.0, 1.0),
                new Vec3(2.0, 0.0, 0.0),
                90.0,
                true);

        assertVectorEquals(new Vec3(0.0, 0.0, 1.0), rotated);
    }

    @Test
    void reverseSpeedReversesLocalRotation() {
        Vec3 forward = new Vec3(0.0, 0.0, 1.0);

        Vec3 normal = RotationOp.rotatedFacing(forward, Vec3.ZERO, 90.0);
        Vec3 reversed = RotationOp.rotatedFacing(forward, Vec3.ZERO, -90.0);

        assertVectorEquals(new Vec3(-1.0, 0.0, 0.0), normal);
        assertVectorEquals(new Vec3(1.0, 0.0, 0.0), reversed);
    }

    @Test
    void codecPreservesSpeedAndElapsedTicks() {
        RotationOp op = new RotationOp(-4.0);
        op.rotationForTick();
        op.rotationForTick();

        CompoundTag encoded = op.savePayload();
        RotationOp loaded = (RotationOp) EntityPayload.loadPayload(encoded).orElseThrow();

        assertEquals(-4.0, loaded.rotationSpeed(), EPSILON);
        assertEquals(2, loaded.elapsedTicks());
        assertEquals(-4.0, loaded.rotationForTick(), EPSILON);
    }

    private static void assertVectorEquals(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }
}
