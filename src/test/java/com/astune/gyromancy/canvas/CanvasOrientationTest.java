package com.astune.gyromancy.canvas;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanvasOrientationTest {

    @Test
    void everyFaceHasAnOrthonormalInPlaneBasis() {
        for (Direction direction : Direction.values()) {
            Vec3 width = CanvasOrientation.widthAxis(direction);
            Vec3 height = CanvasOrientation.heightAxis(direction);
            Vec3 normal = Vec3.atLowerCornerOf(direction.getNormal());

            assertEquals(1.0, width.lengthSqr(), 1.0E-9);
            assertEquals(1.0, height.lengthSqr(), 1.0E-9);
            assertEquals(0.0, width.dot(height), 1.0E-9);
            assertEquals(0.0, width.dot(normal), 1.0E-9);
            assertEquals(0.0, height.dot(normal), 1.0E-9);
            Vec3 cross = width.cross(height);
            assertEquals(normal.x, cross.x, 1.0E-9);
            assertEquals(normal.y, cross.y, 1.0E-9);
            assertEquals(normal.z, cross.z, 1.0E-9);
        }
    }

    @Test
    void floorAndCeilingUseStableReadableOrientations() {
        assertEquals(new Vec3(1, 0, 0), CanvasOrientation.widthAxis(Direction.UP));
        assertEquals(new Vec3(0, 0, -1), CanvasOrientation.heightAxis(Direction.UP));
        assertEquals(new Vec3(1, 0, 0), CanvasOrientation.widthAxis(Direction.DOWN));
        assertEquals(new Vec3(0, 0, 1), CanvasOrientation.heightAxis(Direction.DOWN));
    }
}
