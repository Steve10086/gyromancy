package com.astune.gyromancy.canvas;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasOrientationTest {

    private static final List<Direction> HORIZONTAL = List.of(
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST);

    @Test
    void everyFaceHasAnOrthonormalInPlaneBasis() {
        for (Direction direction : Direction.values()) {
            for (Direction top : topsFor(direction)) {
                Vec3 width = CanvasOrientation.rightAxis(top, direction);
                Vec3 height = CanvasOrientation.topAxis(top);
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
    }

    @Test
    void legacyBasisKeepsFloorAndCeilingReadable() {
        assertEquals(new Vec3(1, 0, 0), CanvasOrientation.widthAxis(Direction.UP));
        assertEquals(new Vec3(0, 0, -1), CanvasOrientation.heightAxis(Direction.UP));
        assertEquals(new Vec3(1, 0, 0), CanvasOrientation.widthAxis(Direction.DOWN));
        assertEquals(new Vec3(0, 0, 1), CanvasOrientation.heightAxis(Direction.DOWN));
    }

    @Test
    void horizontalCanvasesPointTheirTopAwayFromThePlacer() {
        for (Direction viewerFacing : HORIZONTAL) {
            assertEquals(viewerFacing,
                    CanvasOrientation.topDirection(Direction.UP, viewerFacing));
            assertEquals(viewerFacing,
                    CanvasOrientation.topDirection(Direction.DOWN, viewerFacing));
        }
    }

    @Test
    void wallCanvasesStayUprightForEveryPlacerFacing() {
        for (Direction face : HORIZONTAL) {
            for (Direction viewerFacing : HORIZONTAL) {
                assertEquals(Direction.UP,
                        CanvasOrientation.topDirection(face, viewerFacing));
            }
        }
    }

    @Test
    void floorBottomEdgeFacesThePlacerAndCeilingAnchorFlipsSides() {
        // Facing north: right = top x normal = NORTH x UP = EAST.
        assertEquals(new Vec3(1, 0, 0),
                CanvasOrientation.rightAxis(Direction.NORTH, Direction.UP));
        assertFalse(CanvasOrientation.anchorOnPositiveU(Direction.UP));

        // Ceilings face down, so their +U axis points to the viewer's left.
        assertEquals(new Vec3(-1, 0, 0),
                CanvasOrientation.rightAxis(Direction.NORTH, Direction.DOWN));
        assertTrue(CanvasOrientation.anchorOnPositiveU(Direction.DOWN));

        for (Direction face : HORIZONTAL) {
            assertFalse(CanvasOrientation.anchorOnPositiveU(face));
        }
    }

    @Test
    void compatibleTopRejectsAxesParallelToTheSupport() {
        assertEquals(Direction.UP,
                CanvasOrientation.compatibleTop(Direction.SOUTH, Direction.UP));
        assertEquals(Direction.UP,
                CanvasOrientation.compatibleTop(Direction.SOUTH, Direction.NORTH));
        assertEquals(Direction.SOUTH,
                CanvasOrientation.compatibleTop(Direction.UP, Direction.SOUTH));
        assertEquals(Direction.NORTH,
                CanvasOrientation.compatibleTop(Direction.UP, Direction.UP));
        assertEquals(Direction.SOUTH,
                CanvasOrientation.compatibleTop(Direction.DOWN, Direction.DOWN));
    }

    @Test
    void horizontalSideSnapsTheViewerOffset() {
        Vec3 anchor = new Vec3(0.5, 0.0, 0.5);
        assertEquals(Direction.SOUTH, CanvasOrientation.horizontalSide(anchor, new Vec3(0.5, 0.0, 4.5)));
        assertEquals(Direction.EAST, CanvasOrientation.horizontalSide(anchor, new Vec3(4.5, 0.0, 0.5)));
        assertEquals(Direction.NORTH, CanvasOrientation.horizontalSide(anchor, new Vec3(0.5, 0.0, -4.5)));
        assertEquals(Direction.WEST, CanvasOrientation.horizontalSide(anchor, new Vec3(-4.5, 0.0, 0.5)));
        assertNull(CanvasOrientation.horizontalSide(anchor, new Vec3(0.5, 0.0, 0.5)));
    }

    static List<Direction> topsFor(Direction normal) {
        return normal.getAxis().isHorizontal()
                ? List.of(Direction.UP)
                : HORIZONTAL;
    }
}
