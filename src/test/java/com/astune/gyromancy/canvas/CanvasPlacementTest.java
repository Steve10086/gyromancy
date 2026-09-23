package com.astune.gyromancy.canvas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the player-relative placement contract: the clicked block is the
 * canvas' bottom-left corner and the canvas extends to the placer's right and
 * away from them.
 */
class CanvasPlacementTest {
    private static final BlockPos ANCHOR = new BlockPos(10, 64, 10);

    @Test
    void singleBlockCanvasFillsItsAnchorBlock() {
        for (Direction face : Direction.values()) {
            for (Direction top : CanvasOrientationTest.topsFor(face)) {
                AABB bounds = CanvasPlacementGeometry.unfurledBounds(ANCHOR, face, top, 1, 1);
                Vec3 right = CanvasOrientation.rightAxis(top, face);
                Vec3 height = CanvasOrientation.topAxis(top);
                Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
                String context = face + "/" + top;
                assertAxisExtents(bounds, ANCHOR, right, -0.5, 0.5, context + " right");
                assertAxisExtents(bounds, ANCHOR, height, -0.5, 0.5, context + " top");
                // The canvas hugs the support face opposite its normal and
                // extends its thin depth towards the normal.
                assertAxisExtents(bounds, ANCHOR, normal, -0.5, -0.5 + CanvasPlacementGeometry.DEPTH,
                        context + " normal");
            }
        }
    }

    @Test
    void wallCanvasGrowsUpAndToTheViewersRight() {
        AABB bounds = CanvasPlacementGeometry.unfurledBounds(ANCHOR, Direction.SOUTH, Direction.UP, 3, 3);
        // South face: right = EAST, top = UP. The anchor is the bottom-left block.
        assertEquals(ANCHOR.getX(), bounds.minX, 1.0E-9);
        assertEquals(ANCHOR.getX() + 3, bounds.maxX, 1.0E-9);
        assertEquals(ANCHOR.getY(), bounds.minY, 1.0E-9);
        assertEquals(ANCHOR.getY() + 3, bounds.maxY, 1.0E-9);
        assertEquals(ANCHOR.getZ(), bounds.minZ, 1.0E-9);
        assertEquals(ANCHOR.getZ() + CanvasPlacementGeometry.DEPTH, bounds.maxZ, 1.0E-9);
    }

    @Test
    void floorCanvasGrowsAwayFromAPlacerFacingNorth() {
        AABB bounds = CanvasPlacementGeometry.unfurledBounds(ANCHOR, Direction.UP, Direction.NORTH, 3, 2);
        // Floor, placer facing north: right = EAST, top = NORTH (away from them).
        assertEquals(ANCHOR.getX(), bounds.minX, 1.0E-9);
        assertEquals(ANCHOR.getX() + 3, bounds.maxX, 1.0E-9);
        assertEquals(ANCHOR.getY(), bounds.minY, 1.0E-9);
        assertEquals(ANCHOR.getY() + CanvasPlacementGeometry.DEPTH, bounds.maxY, 1.0E-9);
        // The anchor block's south edge is min V, so the canvas extends north.
        assertEquals(ANCHOR.getZ() - 1, bounds.minZ, 1.0E-9);
        assertEquals(ANCHOR.getZ() + 1, bounds.maxZ, 1.0E-9);
    }

    @Test
    void floorCanvasOccupiesDifferentBlocksForDifferentPlacerFacings() {
        AABB facingNorth = CanvasPlacementGeometry.unfurledBounds(ANCHOR, Direction.UP, Direction.NORTH, 2, 2);
        AABB facingEast = CanvasPlacementGeometry.unfurledBounds(ANCHOR, Direction.UP, Direction.EAST, 2, 2);
        // Facing north: grows north and east. Facing east: grows east and south.
        assertEquals(ANCHOR.getZ() - 1, facingNorth.minZ, 1.0E-9);
        assertEquals(ANCHOR.getX() + 2, facingNorth.maxX, 1.0E-9);
        assertEquals(ANCHOR.getX(), facingEast.minX, 1.0E-9);
        assertEquals(ANCHOR.getX() + 2, facingEast.maxX, 1.0E-9);
        assertEquals(ANCHOR.getZ(), facingEast.minZ, 1.0E-9);
        assertEquals(ANCHOR.getZ() + 2, facingEast.maxZ, 1.0E-9);
        // Floor canvases stay within the anchor block's own cell in Y.
        assertTrue(facingNorth.maxY <= ANCHOR.getY() + 1.0 + 1.0E-6);
    }

    @Test
    void ceilingCanvasAnchorsOnThePlacersLeftAndGrowsAway() {
        AABB bounds = CanvasPlacementGeometry.unfurledBounds(ANCHOR, Direction.DOWN, Direction.NORTH, 2, 2);
        // Ceiling, placer facing north: right = WEST (their left), top = NORTH.
        assertEquals(ANCHOR.getX(), bounds.minX, 1.0E-9);
        assertEquals(ANCHOR.getX() + 2, bounds.maxX, 1.0E-9);
        assertEquals(ANCHOR.getY() + 1 - CanvasPlacementGeometry.DEPTH, bounds.minY, 1.0E-9);
        assertEquals(ANCHOR.getY() + 1, bounds.maxY, 1.0E-9);
        assertEquals(ANCHOR.getZ() - 1, bounds.minZ, 1.0E-9);
        assertEquals(ANCHOR.getZ() + 1, bounds.maxZ, 1.0E-9);
    }

    @Test
    void scrollOriginSitsOnTheCanvasBottomEdge() {
        for (Direction face : Direction.values()) {
            for (Direction top : CanvasOrientationTest.topsFor(face)) {
                int height = 4;
                AABB bounds = CanvasPlacementGeometry.unfurledBounds(ANCHOR, face, top, 2, height);
                Vec3 right = CanvasOrientation.rightAxis(top, face);
                Vec3 topAxis = CanvasOrientation.topAxis(top);
                Vec3 origin = CanvasScrollGeometry.origin(ANCHOR, face, right, topAxis);
                assertEquals(-height * 0.5,
                        origin.subtract(bounds.getCenter()).dot(topAxis), 1.0E-9,
                        face + "/" + top);
                // The scroll stays inside the anchor block in the width axis.
                double alongRight = origin.subtract(Vec3.atCenterOf(ANCHOR)).dot(right);
                assertTrue(Math.abs(alongRight) <= 0.5 + 1.0E-9, face + "/" + top);
            }
        }
    }

    private static void assertAxisExtents(AABB bounds, BlockPos block, Vec3 axis,
                                          double min, double max, String context) {
        double actualMin = Double.POSITIVE_INFINITY;
        double actualMax = Double.NEGATIVE_INFINITY;
        Vec3 center = Vec3.atCenterOf(block);
        for (int corner = 0; corner < 8; corner++) {
            Vec3 point = new Vec3(
                    (corner & 1) == 0 ? bounds.minX : bounds.maxX,
                    (corner & 2) == 0 ? bounds.minY : bounds.maxY,
                    (corner & 4) == 0 ? bounds.minZ : bounds.maxZ);
            double projected = point.subtract(center).dot(axis);
            actualMin = Math.min(actualMin, projected);
            actualMax = Math.max(actualMax, projected);
        }
        assertEquals(min, actualMin, 1.0E-9, context);
        assertEquals(max, actualMax, 1.0E-9, context);
    }
}
