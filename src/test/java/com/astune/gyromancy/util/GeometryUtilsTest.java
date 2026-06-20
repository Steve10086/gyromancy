package com.astune.gyromancy.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class GeometryUtilsTest {

    // ═══════════════════ Fourier Descriptors ═══════════════════

    @Test
    @DisplayName("FD of identical shapes should have zero distance")
    void testFdIdentical() {
        int[][] img = createCircleShape();
        float[] d1 = GeometryUtils.fourierDescriptor(img);
        float[] d2 = GeometryUtils.fourierDescriptor(img);
        assertEquals(0f, GeometryUtils.fdDistance(d1, d2), 0.01f);
    }

    @Test
    @DisplayName("FD of circle vs square should differ")
    void testFdDifferent() {
        float[] d1 = GeometryUtils.fourierDescriptor(createCircleShape());
        float[] d2 = GeometryUtils.fourierDescriptor(createSquareShape());
        float dist = GeometryUtils.fdDistance(d1, d2);
        assertTrue(dist > 0.01f,
                "Circle vs square FD should differ, got " + dist);
    }

    @Test
    @DisplayName("FD score should be 1.0 for zero distance")
    void testFdScore() {
        assertEquals(1f, GeometryUtils.fdScore(0f), 0.01f);
        assertTrue(GeometryUtils.fdScore(1f) < 0.6f);
    }

    @Test
    @DisplayName("FD of symmetric shapes (circle) should be repeatable")
    void testFdCircleRepeatable() {
        float[] d1 = GeometryUtils.fourierDescriptor(createCircleShape());
        float[] d2 = GeometryUtils.fourierDescriptor(createCircleShape());
        assertArrayEquals(d1, d2, 0.001f);
    }

    // ═══════════════════ Boundary tracing ═══════════════════

    @Test
    @DisplayName("traceBoundary of circle should produce 50+ points")
    void testBoundaryCircle() {
        var contour = GeometryUtils.traceBoundary(createCircleShape());
        assertTrue(contour.size() > 50, "Circle boundary should have 50+ points, got " + contour.size());
    }

    @Test
    @DisplayName("traceBoundary of empty image should be empty")
    void testBoundaryEmpty() {
        var contour = GeometryUtils.traceBoundary(new int[32][32]);
        assertTrue(contour.isEmpty());
    }

    // ═══════════════════ Bounding box & centroid ═══════════════════

    @Test
    @DisplayName("Bounding box should be correct")
    void testBoundingBox() {
        int[][] img = new int[32][32];
        for (int y = 10; y <= 15; y++) for (int x = 8; x <= 12; x++) img[y][x] = 1;
        int[] bbox = GeometryUtils.computeBoundingBox(img);
        assertEquals(8, bbox[0]); assertEquals(10, bbox[1]);
        assertEquals(12, bbox[2]); assertEquals(15, bbox[3]);
    }

    @Test
    @DisplayName("Centroid of symmetric shape should be near center")
    void testCentroid() {
        int[][] img = createSquareShape();
        double[] c = GeometryUtils.computeCentroid(img);
        assertEquals(15.5, c[0], 0.5);
        assertEquals(15.5, c[1], 0.5);
    }

    // ═══════════════════ Helpers ═══════════════════

    private static int[][] createSquareShape() {
        int[][] img = new int[32][32];
        int m = 6, s = 20;
        for (int y = m; y < m + s; y++)
            for (int x = m; x < m + s; x++)  // hollow: outline only
                if (y == m || y == m + s - 1 || x == m || x == m + s - 1) img[y][x] = 1;
        return img;
    }

    private static int[][] createCircleShape() {
        int[][] img = new int[32][32];
        double cx = 15.5, cy = 15.5, outer = 14, inner = 10;
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 32; x++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
                if (d >= inner && d <= outer) img[y][x] = 1;
            }
        return img;
    }
}
