package com.astune.gyromancy.api.field;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RectangularFieldShapeTest {
    @Test
    void defaultsToAOneBlockCube() {
        RectangularFieldShape shape = new RectangularFieldShape();

        assertEquals(1.0F, shape.length());
        assertEquals(1.0F, shape.width());
        assertEquals(1.0F, shape.height());
        assertEquals(1.0, shape.volume());
        assertTrue(shape.isInside(new Vec3(0.5, 0.5, 0.5)));
        assertFalse(shape.isInside(new Vec3(0.5001, 0.0, 0.0)));
    }

    @Test
    void capsEveryDimensionAtSixteenBlocks() {
        assertEquals(RectangularFieldShape.MAX_SIZE,
                new RectangularFieldShape(16.0F, 16.0F, 16.0F).length());
        assertThrows(IllegalArgumentException.class,
                () -> new RectangularFieldShape(16.01F, 1.0F, 1.0F));
        assertThrows(IllegalArgumentException.class,
                () -> new RectangularFieldShape(0.0F, 1.0F, 1.0F));
    }

    @Test
    void usesTheSuppliedFieldOrientationForMembershipAndBroadPhaseBounds() {
        RectangularFieldShape shape = new RectangularFieldShape(4.0F, 2.0F, 1.0F);
        ShapeOrientation orientation = new ShapeOrientation(new Vec3(0.0, 0.0, 1.0), 90.0F);

        // Length stays on local +Z (forward); the roll maps width onto world Y.
        assertTrue(shape.isInside(new Vec3(0.0, 0.75, 0.0), orientation));
        assertFalse(shape.isInside(new Vec3(0.0, 1.01, 0.0), orientation));
        assertEquals(1.0, shape.bounds(orientation).getXsize(), 1.0E-6);
        assertEquals(2.0, shape.bounds(orientation).getYsize(), 1.0E-6);
        assertEquals(4.0, shape.bounds(orientation).getZsize(), 1.0E-6);
    }

    @Test
    void alignsItsLengthWithTheBoundForwardAxis() {
        RectangularFieldShape shape = new RectangularFieldShape(4.0F, 2.0F, 1.0F);
        ShapeOrientation orientation = new ShapeOrientation(new Vec3(1.0, 0.0, 0.0), 0.0F);

        assertTrue(shape.isInside(new Vec3(1.99, 0.0, 0.0), orientation));
        assertFalse(shape.isInside(new Vec3(2.01, 0.0, 0.0), orientation));
        assertFalse(shape.isInside(new Vec3(0.0, 0.0, 1.01), orientation));
    }

    @Test
    void calculatesTheExactDistanceToItsDirectionalBoundary() {
        RectangularFieldShape shape = new RectangularFieldShape(4.0F, 2.0F, 1.0F);
        ShapeOrientation orientation = ShapeOrientation.IDENTITY;

        assertEquals(2.0, shape.supportDistance(orientation, new Vec3(0.0, 0.0, 1.0)), 1.0E-6);
        assertEquals(1.0, shape.supportDistance(orientation, new Vec3(1.0, 0.0, 0.0)), 1.0E-6);
        assertEquals(0.5, shape.supportDistance(orientation, new Vec3(0.0, 1.0, 0.0)), 1.0E-6);
    }
}
