package com.astune.gyromancy.api.geometry;

import com.astune.gyromancy.symbol.FloodFillExtractor;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceFrameTest {
    private static final double EPSILON = 1.0E-9;

    @Test
    void arbitraryFrameRoundTripsWorldAndSurfaceCoordinates() {
        Vec3 normal = new Vec3(1.0, 1.0, 1.0).normalize();
        Vec3 axisU = new Vec3(1.0, -1.0, 0.0).normalize();
        Vec3 axisV = normal.cross(axisU).normalize();
        SurfaceFrame frame = new SurfaceFrame(
                new Vec3(3.0, 4.0, 5.0), axisU, axisV, normal);

        Vec3 world = frame.world(2.25, -1.75);
        SurfaceFrame.Coordinates coordinates = frame.project(world);

        assertEquals(2.25, coordinates.u(), EPSILON);
        assertEquals(-1.75, coordinates.v(), EPSILON);
        assertEquals(0.0, frame.signedDistance(world), EPSILON);
        assertFalse(frame.axisAlignedDirection().isPresent());
    }

    @Test
    void sixDirectionAdapterPreservesLegacyFlattenCoordinates() {
        Vec3 point = new Vec3(3.25, 4.5, 5.75);
        for (Direction face : Direction.values()) {
            SurfaceFrame frame = SurfaceFrame.fromBlockFace(new BlockPos(3, 4, 5), face);
            SurfaceFrame.Coordinates coordinates = frame.project(point);
            double[] legacy = FloodFillExtractor.flatten(face, point);

            assertEquals(legacy[0], coordinates.u(), EPSILON, face.toString());
            assertEquals(legacy[1], coordinates.v(), EPSILON, face.toString());
            assertEquals(face, frame.requireAxisAlignedDirection());
            assertTrue(frame.legacyBlockFaceAt(frame.world(3.25, 4.5)).isPresent());
        }
    }

    @Test
    void facingFrameKeepsWorldUpAndScreenRightAcrossOppositeYaw() {
        Vec3 north = new Vec3(0.0, 0.0, -1.0);
        Vec3 south = new Vec3(0.0, 0.0, 1.0);
        SurfaceFrame northView = SurfaceFrame.facing(
                Vec3.ZERO, north, new Vec3(0.0, 1.0, 0.0));
        SurfaceFrame southView = SurfaceFrame.facing(
                Vec3.ZERO, south, new Vec3(0.0, 1.0, 0.0));

        assertVec3(new Vec3(1.0, 0.0, 0.0), northView.axisU());
        assertVec3(new Vec3(0.0, 1.0, 0.0), northView.axisV());
        assertVec3(new Vec3(-1.0, 0.0, 0.0), southView.axisU());
        assertVec3(new Vec3(0.0, 1.0, 0.0), southView.axisV());
        assertVec3(north, northView.normal());
        assertVec3(south, southView.normal());
        assertVec3(north.scale(-1.0), northView.axisU().cross(northView.axisV()));
        assertVec3(south.scale(-1.0), southView.axisU().cross(southView.axisV()));
    }

    @Test
    void codecPreservesFreeOrientation() {
        Vec3 normal = new Vec3(0.25, 0.5, 0.75).normalize();
        Vec3 axisU = normal.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
        Vec3 axisV = normal.cross(axisU).normalize();
        SurfaceFrame frame = new SurfaceFrame(new Vec3(8.0, -2.0, 4.0),
                axisU, axisV, normal);

        var encoded = SurfaceFrame.CODEC.encodeStart(JsonOps.INSTANCE, frame).getOrThrow();
        SurfaceFrame decoded = SurfaceFrame.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(frame, decoded);
    }

    private static void assertVec3(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }
}
