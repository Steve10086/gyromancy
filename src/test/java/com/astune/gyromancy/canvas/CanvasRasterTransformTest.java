package com.astune.gyromancy.canvas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CanvasRasterTransformTest {
    /** 3x3 matrix with a distinct value in every cell. */
    private static final int[] SOURCE = {1, 2, 3, 4, 5, 6, 7, 8, 9};

    @Test
    void clockwiseTurnMovesTheTopLeftCornerToTheTopRight() {
        // 1 2 3      7 4 1
        // 4 5 6  ->  8 5 2
        // 7 8 9      9 6 3
        assertArrayEquals(new int[]{7, 4, 1, 8, 5, 2, 9, 6, 3},
                CanvasRasterTransform.rotateClockwise(SOURCE, 3));
    }

    @Test
    void counterClockwiseTurnMovesTheTopRightCornerToTheTopLeft() {
        // 1 2 3      3 6 9
        // 4 5 6  ->  2 5 8
        // 7 8 9      1 4 7
        assertArrayEquals(new int[]{3, 6, 9, 2, 5, 8, 1, 4, 7},
                CanvasRasterTransform.rotateCounterClockwise(SOURCE, 3));
    }

    @Test
    void mirrorsFlipTheExpectedAxis() {
        // 1 2 3      3 2 1
        // 4 5 6  ->  6 5 4
        // 7 8 9      9 8 7
        assertArrayEquals(new int[]{3, 2, 1, 6, 5, 4, 9, 8, 7},
                CanvasRasterTransform.mirrorHorizontally(SOURCE, 3));
        // 1 2 3      7 8 9
        // 4 5 6  ->  4 5 6
        // 7 8 9      1 2 3
        assertArrayEquals(new int[]{7, 8, 9, 4, 5, 6, 1, 2, 3},
                CanvasRasterTransform.mirrorVertically(SOURCE, 3));
    }

    @Test
    void repeatedTransformsReturnToTheOriginalRaster() {
        int[] rotated = CanvasRasterTransform.rotateClockwise(SOURCE, 3);
        for (int turn = 0; turn < 3; turn++) {
            rotated = CanvasRasterTransform.rotateClockwise(rotated, 3);
        }
        assertArrayEquals(SOURCE, rotated);

        assertArrayEquals(SOURCE, CanvasRasterTransform.rotateCounterClockwise(
                CanvasRasterTransform.rotateClockwise(SOURCE, 3), 3));
        assertArrayEquals(SOURCE, CanvasRasterTransform.mirrorHorizontally(
                CanvasRasterTransform.mirrorHorizontally(SOURCE, 3), 3));
        assertArrayEquals(SOURCE, CanvasRasterTransform.mirrorVertically(
                CanvasRasterTransform.mirrorVertically(SOURCE, 3), 3));
    }

    @Test
    void counterClockwiseEqualsThreeClockwiseTurns() {
        int[] threeTurns = SOURCE;
        for (int turn = 0; turn < 3; turn++) {
            threeTurns = CanvasRasterTransform.rotateClockwise(threeTurns, 3);
        }
        assertArrayEquals(threeTurns, CanvasRasterTransform.rotateCounterClockwise(SOURCE, 3));
    }

    @Test
    void rejectsNonSquareRasters() {
        assertThrows(IllegalArgumentException.class,
                () -> CanvasRasterTransform.rotateClockwise(new int[6], 3));
        assertThrows(IllegalArgumentException.class,
                () -> CanvasRasterTransform.mirrorVertically(new int[4], 0));
    }
}
