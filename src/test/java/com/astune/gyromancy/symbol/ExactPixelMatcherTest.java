package com.astune.gyromancy.symbol;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ExactPixelMatcherTest {

    @Test
    void matchesAllFourQuarterTurnsWithoutChangingTheInput() {
        int[][] template = {
                {1, 0, 0},
                {1, 1, 0},
                {0, 0, 1}
        };
        int[][] original = copy(template);

        assertEquals(0, ExactPixelMatcher.matchingRotation(template, template).orElseThrow());
        assertEquals(90, ExactPixelMatcher.matchingRotation(clockwise(template), template).orElseThrow());
        assertEquals(180, ExactPixelMatcher.matchingRotation(clockwise(clockwise(template)), template).orElseThrow());
        assertEquals(270, ExactPixelMatcher.matchingRotation(clockwise(clockwise(clockwise(template))), template).orElseThrow());

        assertMatrixEquals(original, template);
    }

    @Test
    void requiresMatchingDimensionsAndEveryPixelToMatch() {
        int[][] template = {
                {1, 0, 0},
                {0, 1, 0},
                {0, 0, 1}
        };
        int[][] changed = copy(template);
        changed[1][1] = 0;

        assertFalse(ExactPixelMatcher.matchingRotation(changed, template).isPresent());
        assertFalse(ExactPixelMatcher.matchingRotation(new int[2][3], template).isPresent());
        assertFalse(ExactPixelMatcher.matchingRotation(new int[3][3], new int[4][4]).isPresent());
    }

    @Test
    void matchesRectangularPatternsAndSwapsDimensionsOnRotation() {
        int[][] template = {
                {1, 0},
                {1, 1},
                {0, 0}
        };

        assertEquals(0, ExactPixelMatcher.matchingRotation(template, template).orElseThrow());
        assertEquals(90, ExactPixelMatcher.matchingRotation(clockwise(template), template).orElseThrow());
        assertEquals(180, ExactPixelMatcher.matchingRotation(
                clockwise(clockwise(template)), template).orElseThrow());
        assertEquals(270, ExactPixelMatcher.matchingRotation(
                clockwise(clockwise(clockwise(template))), template).orElseThrow());
    }

    @Test
    void returnsFirstRotationForSymmetricTemplate() {
        int[][] template = {
                {1, 1},
                {1, 1}
        };

        assertEquals(OptionalInt.of(0), ExactPixelMatcher.matchingRotation(template, template));
    }

    private static int[][] clockwise(int[][] source) {
        int height = source.length;
        int width = source[0].length;
        int[][] result = new int[width][height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                result[x][height - 1 - y] = source[y][x];
            }
        }
        return result;
    }

    private static int[][] copy(int[][] source) {
        int[][] result = new int[source.length][];
        for (int y = 0; y < source.length; y++) result[y] = source[y].clone();
        return result;
    }

    private static void assertMatrixEquals(int[][] expected, int[][] actual) {
        assertEquals(expected.length, actual.length);
        for (int y = 0; y < expected.length; y++) assertArrayEquals(expected[y], actual[y]);
    }
}
