package com.astune.gyromancy.api.field;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShapeOrientationTest {
    @Test
    void mapsTheLocalForwardAxisToAnArbitraryFieldSpaceDirection() {
        ShapeOrientation orientation = new ShapeOrientation(new Vec3(1.0, 0.0, 0.0), 0.0F);

        assertVectorEquals(new Vec3(1.0, 0.0, 0.0),
                orientation.toFieldSpace(new Vec3(0.0, 0.0, 1.0)));
        assertVectorEquals(new Vec3(0.0, 0.0, 1.0),
                orientation.toLocalSpace(new Vec3(1.0, 0.0, 0.0)));
    }

    @Test
    void copiesTheBoundFieldDirectionRatherThanSharingItsVector() {
        FieldDirection direction = new FieldDirection(new Vec3(2.0, 0.0, 0.0), 45.0F);
        ShapeOrientation orientation = new ShapeOrientation(direction);

        assertVectorEquals(direction.vector(), orientation.forward());
        assertEquals(direction.angleDegrees(), orientation.rollDegrees());
        org.junit.jupiter.api.Assertions.assertNotSame(direction.vector(), orientation.forward());
    }

    private static void assertVectorEquals(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 1.0E-10);
        assertEquals(expected.y, actual.y, 1.0E-10);
        assertEquals(expected.z, actual.z, 1.0E-10);
    }
}
