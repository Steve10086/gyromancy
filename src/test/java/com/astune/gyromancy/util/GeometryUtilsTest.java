package com.astune.gyromancy.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GeometryUtils — pure math, no Minecraft dependency needed.
 */
class GeometryUtilsTest {

    // ═══════════════════════════════════════════════════════════════
    // Hu Moments
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Hu moments should be nearly identical for rotated versions of the same shape")
    void testHuMomentsRotationInvariance() {
        int[][] original = createSquareShape(10, 10, 5);
        int[][] rotated = rotate90(createSquareShape(10, 10, 5));

        double[] huOrig = GeometryUtils.computeHuMoments(original);
        double[] huRot = GeometryUtils.computeHuMoments(rotated);

        double dist = GeometryUtils.huDistance(huOrig, huRot);
        assertTrue(dist < 1.0,
                "Hu distance between original and rotated should be small, got: " + dist);
    }

    @Test
    @DisplayName("Hu moments for completely different shapes should have large distance")
    void testHuMomentsDifferentShapes() {
        int[][] shape1 = createSquareShape(16, 16, 6);
        int[][] shape2 = createHorizontalBar(16, 16);

        double[] hu1 = GeometryUtils.computeHuMoments(shape1);
        double[] hu2 = GeometryUtils.computeHuMoments(shape2);

        double dist = GeometryUtils.huDistance(hu1, hu2);
        assertTrue(dist > 0.5,
                "Hu distance between different shapes should be large, got: " + dist);
    }

    @Test
    @DisplayName("Hu moments for empty image should be all zeros")
    void testHuMomentsEmptyImage() {
        int[][] empty = new int[16][16];
        double[] hu = GeometryUtils.computeHuMoments(empty);
        for (double v : hu) {
            assertEquals(0.0, v, 1e-10);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PCA
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PCA of horizontal rectangle should give angle near 0")
    void testPCAHorizontalRectangle() {
        int[][] image = createHorizontalBar(16, 16);
        GeometryUtils.PCAResult result = GeometryUtils.computePCA(image);

        // Horizontal bar → angle should be near 0 or 180
        double angle = result.angleDegrees();
        assertTrue(angle < 20 || angle > 160,
                "Horizontal bar angle should be near 0 or 180, got: " + angle);
    }

    @Test
    @DisplayName("PCA of vertical rectangle should give angle near 90")
    void testPCAVerticalRectangle() {
        int[][] image = createVerticalBar(16, 16);
        GeometryUtils.PCAResult result = GeometryUtils.computePCA(image);

        // Vertical bar → angle should be near 90
        double angle = result.angleDegrees();
        assertTrue(angle > 70 && angle < 110,
                "Vertical bar angle should be near 90, got: " + angle);
    }

    // ═══════════════════════════════════════════════════════════════
    // Bounding box & centroid
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Bounding box should correctly identify min/max")
    void testBoundingBox() {
        int[][] image = new int[16][16];
        // Draw a 3x3 block at (4,5)-(6,7)
        for (int y = 5; y <= 7; y++)
            for (int x = 4; x <= 6; x++)
                image[y][x] = 1;

        int[] bbox = GeometryUtils.computeBoundingBox(image);
        assertEquals(4, bbox[0], "minX");
        assertEquals(5, bbox[1], "minY");
        assertEquals(6, bbox[2], "maxX");
        assertEquals(7, bbox[3], "maxY");
    }

    @Test
    @DisplayName("Centroid of symmetric shape should be at center")
    void testCentroidSymmetric() {
        int[][] image = createSquareShape(16, 16, 4);
        double[] centroid = GeometryUtils.computeCentroid(image);
        // Center of 16×16 is at (7.5, 7.5)
        assertEquals(7.5, centroid[0], 0.5);
        assertEquals(7.5, centroid[1], 0.5);
    }

    // ═══════════════════════════════════════════════════════════════
    // Normalization
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Normalized image should have correct target dimensions")
    void testNormalizeDimensions() {
        int[][] source = createSquareShape(16, 16, 5);
        int[][] normalized = GeometryUtils.normalize(source, 32, 32);

        assertEquals(32, normalized.length);
        assertEquals(32, normalized[0].length);
    }

    @Test
    @DisplayName("Normalized image should have non-zero pixels")
    void testNormalizeNonEmpty() {
        int[][] source = createSquareShape(16, 16, 8);
        int[][] normalized = GeometryUtils.normalize(source, 32, 32);

        int area = GeometryUtils.computeArea(normalized);
        assertTrue(area > 0, "Normalized image should have foreground pixels");
    }

    // ═══════════════════════════════════════════════════════════════
    // Overlap score
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Overlap score of identical images should be 1.0")
    void testOverlapScoreIdentical() {
        int[][] image = createSquareShape(16, 16, 5);
        double score = GeometryUtils.overlapScore(image, image);
        assertEquals(1.0, score, 0.01);
    }

    @Test
    @DisplayName("Overlap score of completely different images should be 0.0")
    void testOverlapScoreDifferent() {
        int[][] img1 = createSquareShape(16, 16, 5);
        // Fill a region that doesn't overlap with img1
        int[][] img2 = new int[16][16];
        for (int y = 0; y < 2; y++)
            for (int x = 0; x < 2; x++)
                img2[y][x] = 1;

        double score = GeometryUtils.overlapScore(img1, img2);
        assertEquals(0.0, score, 0.01);
    }

    // ═══════════════════════════════════════════════════════════════
    // Edge histogram
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Edge histogram should have 8 bins")
    void testEdgeHistogramBins() {
        int[][] image = createSquareShape(16, 16, 5);
        int[] hist = GeometryUtils.computeEdgeHistogram(image);
        assertEquals(8, hist.length);
    }

    @Test
    @DisplayName("Edge histogram distance should be 0 for identical images")
    void testEdgeHistogramDistanceIdentical() {
        int[][] image = createSquareShape(16, 16, 5);
        int[] hist = GeometryUtils.computeEdgeHistogram(image);
        double dist = GeometryUtils.edgeHistogramDistance(hist, hist);
        assertEquals(0.0, dist, 0.01);
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    /** Creates a solid square centered in the grid */
    private static int[][] createSquareShape(int w, int h, int halfSize) {
        int[][] image = new int[h][w];
        int cx = w / 2, cy = h / 2;
        for (int y = cy - halfSize; y <= cy + halfSize; y++) {
            for (int x = cx - halfSize; x <= cx + halfSize; x++) {
                if (y >= 0 && y < h && x >= 0 && x < w) {
                    image[y][x] = 1;
                }
            }
        }
        return image;
    }

    /** Creates a horizontal bar */
    private static int[][] createHorizontalBar(int w, int h) {
        int[][] image = new int[h][w];
        for (int y = h / 2 - 1; y <= h / 2 + 1; y++) {
            for (int x = 2; x < w - 2; x++) {
                if (y >= 0 && y < h) image[y][x] = 1;
            }
        }
        return image;
    }

    /** Creates a vertical bar */
    private static int[][] createVerticalBar(int w, int h) {
        int[][] image = new int[h][w];
        for (int x = w / 2 - 1; x <= w / 2 + 1; x++) {
            for (int y = 2; y < h - 2; y++) {
                if (x >= 0 && x < w) image[y][x] = 1;
            }
        }
        return image;
    }

    /** Rotates a square image 90 degrees clockwise */
    private static int[][] rotate90(int[][] image) {
        int h = image.length, w = image[0].length;
        int[][] rotated = new int[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                rotated[x][h - 1 - y] = image[y][x];
            }
        }
        return rotated;
    }
}
