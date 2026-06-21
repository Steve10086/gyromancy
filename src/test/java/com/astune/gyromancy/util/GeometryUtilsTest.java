package com.astune.gyromancy.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class GeometryUtilsTest {

    @Test
    @DisplayName("SSIM of identical images should be 1.0")
    void testSsimIdentical() {
        int[][] img = createCircleShape();
        assertEquals(1f, GeometryUtils.ssim(img, img), 0.01f);
    }

    @Test
    @DisplayName("SSIM of circle vs square should be &lt; 0.9")
    void testSsimDifferent() {
        float s = GeometryUtils.ssim(createCircleShape(), createSquareShape());
        assertTrue(s < 0.9f, "Circle vs square SSIM should be < 0.9, got " + s);
    }

    @Test
    @DisplayName("SSIM of empty vs empty should be 1.0")
    void testSsimEmpty() {
        int[][] e = new int[32][32];
        assertEquals(1f, GeometryUtils.ssim(e, e), 0.01f);
    }

    @Test
    @DisplayName("SSIM of full vs empty should be low")
    void testSsimFullVsEmpty() {
        int[][] e = new int[32][32];
        int[][] f = new int[32][32];
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) f[y][x] = 1;
        assertTrue(GeometryUtils.ssim(e, f) < 0.1f);
    }

    @Test
    @DisplayName("PCA of horizontal bar should give angle near 0")
    void testPcaHorizontal() {
        int[][] img = horizontalBar();
        var pca = GeometryUtils.computePCA(img);
        assertTrue(pca.angleDegrees() < 20 || pca.angleDegrees() > 160,
                "Horizontal bar angle should be 0±20 or 180±20, got " + pca.angleDegrees());
    }

    @Test
    @DisplayName("PCA of vertical bar should give angle near 90")
    void testPcaVertical() {
        int[][] img = verticalBar();
        var pca = GeometryUtils.computePCA(img);
        assertTrue(pca.angleDegrees() > 70 && pca.angleDegrees() < 110,
                "Vertical bar angle should be 90±20, got " + pca.angleDegrees());
    }

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

    // helpers

    private static int[][] createSquareShape() {
        int[][] img = new int[32][32];
        int m = 6, s = 20;
        for (int y = m; y < m + s; y++)
            for (int x = m; x < m + s; x++)
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

    private static int[][] horizontalBar() {
        int[][] img = new int[32][32];
        for (int x = 4; x < 28; x++) { img[14][x] = 1; img[15][x] = 1; img[16][x] = 1; }
        return img;
    }

    private static int[][] verticalBar() {
        int[][] img = new int[32][32];
        for (int y = 4; y < 28; y++) { img[y][14] = 1; img[y][15] = 1; img[y][16] = 1; }
        return img;
    }
}
