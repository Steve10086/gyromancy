package com.astune.gyromancy.symbol;

import java.util.OptionalInt;

/**
 * Exact matcher for small binary images.
 *
 * <p>The matcher intentionally only supports quarter-turn rotations. It reads
 * both matrices without normalizing, cropping, or modifying either one, so a
 * failed exact comparison can safely be followed by the regular matcher.
 */
public final class ExactPixelMatcher {

    private static final int ROTATION_STEP_DEGREES = 90;
    private static final int ROTATION_COUNT = 4;

    private ExactPixelMatcher() {}

    /**
     * Finds the first exact quarter-turn match of {@code image} in
     * {@code template}.
     *
     * <p>A returned angle describes the clockwise rotation from the template
     * to the input image. Rotations are checked in this order: 0, 90, 180,
     * and 270 degrees. Returning the first match avoids duplicate results for
     * rotationally symmetric templates.
     *
     * @return the matching clockwise angle, or an empty result when either
     * matrix is malformed, their dimensions differ at every rotation, or any
     * pixel differs
     */
    public static OptionalInt matchingRotation(int[][] image, int[][] template) {
        if (dimensions(image) == null || dimensions(template) == null) {
            return OptionalInt.empty();
        }

        for (int rotation = 0; rotation < ROTATION_COUNT; rotation++) {
            int degrees = rotation * ROTATION_STEP_DEGREES;
            if (matchesAtRotation(image, template, degrees)) {
                return OptionalInt.of(degrees);
            }
        }
        return OptionalInt.empty();
    }

    /** Returns whether two matrices match at the requested quarter turn. */
    static boolean matchesAtRotation(int[][] image, int[][] template, int rotationDegrees) {
        MatrixSize imageSize = dimensions(image);
        MatrixSize templateSize = dimensions(template);
        if (imageSize == null || templateSize == null || rotationDegrees % 90 != 0) {
            return false;
        }

        int normalizedRotation = Math.floorMod(rotationDegrees, 360);
        if (normalizedRotation % 90 != 0) return false;

        boolean swapsDimensions = normalizedRotation == 90 || normalizedRotation == 270;
        int expectedHeight = swapsDimensions ? templateSize.width : templateSize.height;
        int expectedWidth = swapsDimensions ? templateSize.height : templateSize.width;
        if (imageSize.height != expectedHeight || imageSize.width != expectedWidth) {
            return false;
        }

        for (int y = 0; y < imageSize.height; y++) {
            for (int x = 0; x < imageSize.width; x++) {
                int templateX;
                int templateY;
                switch (normalizedRotation) {
                    case 0 -> {
                        templateX = x;
                        templateY = y;
                    }
                    case 90 -> {
                        templateX = y;
                        templateY = templateSize.height - 1 - x;
                    }
                    case 180 -> {
                        templateX = templateSize.width - 1 - x;
                        templateY = templateSize.height - 1 - y;
                    }
                    case 270 -> {
                        templateX = templateSize.width - 1 - y;
                        templateY = x;
                    }
                    default -> throw new AssertionError("Unexpected rotation: " + normalizedRotation);
                }

                if (image[y][x] != template[templateY][templateX]) return false;
            }
        }
        return true;
    }

    private static MatrixSize dimensions(int[][] matrix) {
        if (matrix == null || matrix.length == 0 || matrix[0] == null
                || matrix[0].length == 0) return null;
        int width = matrix[0].length;
        for (int[] row : matrix) {
            if (row == null || row.length != width) return null;
        }
        return new MatrixSize(matrix.length, width);
    }

    private record MatrixSize(int height, int width) {}
}
