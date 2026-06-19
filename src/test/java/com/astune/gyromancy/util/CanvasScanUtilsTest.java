package com.astune.gyromancy.util;

import com.astune.gyromancy.util.CanvasScanUtils.ConnectedComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CanvasScanUtils connected component extraction.
 */
class CanvasScanUtilsTest {

    @Test
    @DisplayName("Should extract a single connected component")
    void testExtractSingleComponent() {
        int[][] pixels = createBlob(16, 16, 4, 8, 8);
        List<ConnectedComponent> comps = CanvasScanUtils.extractComponents(pixels, 10);
        assertEquals(1, comps.size());
        assertEquals(81, comps.getFirst().area()); // 9×9 blob
    }

    @Test
    @DisplayName("Should extract multiple connected components")
    void testExtractMultipleComponents() {
        int[][] pixels = new int[16][16];
        // Blob 1 at (3,3)
        fillSquare(pixels, 3, 3, 3);
        // Blob 2 at (11,11)
        fillSquare(pixels, 11, 11, 3);

        List<ConnectedComponent> comps = CanvasScanUtils.extractComponents(pixels, 5);
        assertEquals(2, comps.size());
    }

    @Test
    @DisplayName("Should return empty for blank image")
    void testExtractNoComponents() {
        int[][] pixels = new int[16][16];
        List<ConnectedComponent> comps = CanvasScanUtils.extractComponents(pixels, 1);
        assertTrue(comps.isEmpty());
    }

    @Test
    @DisplayName("Should filter out small components below minArea")
    void testMinAreaFilter() {
        int[][] pixels = new int[16][16];
        // Big blob
        fillSquare(pixels, 2, 2, 5);
        // Tiny dot
        pixels[14][14] = 1;

        List<ConnectedComponent> comps = CanvasScanUtils.extractComponents(pixels, 10);
        assertEquals(1, comps.size()); // Only the big one
    }

    @Test
    @DisplayName("Should correctly identify 8-connected components")
    void testDiagonalConnectivity() {
        int[][] pixels = new int[16][16];
        // Diagonal line — 8-connected means this is ONE component
        for (int i = 0; i < 6; i++) {
            pixels[i + 2][i + 2] = 1;
        }

        List<ConnectedComponent> comps = CanvasScanUtils.extractComponents(pixels, 1);
        assertEquals(1, comps.size());
    }

    @Test
    @DisplayName("Should filter by aspect ratio")
    void testAspectRatioFilter() {
        // Create a long thin horizontal line
        int[][] horizontalLine = new int[16][16];
        for (int x = 1; x < 15; x++) {
            horizontalLine[8][x] = 1;
        }

        List<ConnectedComponent> comps = CanvasScanUtils.extractComponents(horizontalLine, 5);
        // Filter with maxAspectRatio=2.0
        List<ConnectedComponent> filtered = CanvasScanUtils.filterByAspectRatio(comps, 2.0);
        assertTrue(filtered.isEmpty(), "Long thin line should be filtered by aspect ratio");
    }

    @Test
    @DisplayName("Should return components sorted by area descending")
    void testSortedByArea() {
        int[][] pixels = new int[16][16];
        fillSquare(pixels, 2, 2, 2);    // small 5×5
        fillSquare(pixels, 10, 2, 4);   // larger 9×9
        fillSquare(pixels, 2, 10, 1);   // tiny 3×3

        List<ConnectedComponent> comps = CanvasScanUtils.extractComponents(pixels, 1);
        assertEquals(3, comps.size());
        assertTrue(comps.get(0).area() >= comps.get(1).area(),
                "First component should be largest");
        assertTrue(comps.get(1).area() >= comps.get(2).area(),
                "Second component should be >= third");
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private static int[][] createBlob(int w, int h, int halfSize, int cx, int cy) {
        int[][] image = new int[h][w];
        fillSquare(image, cx, cy, halfSize);
        return image;
    }

    private static void fillSquare(int[][] pixels, int cx, int cy, int halfSize) {
        for (int y = cy - halfSize; y <= cy + halfSize; y++) {
            for (int x = cx - halfSize; x <= cx + halfSize; x++) {
                if (y >= 0 && y < pixels.length && x >= 0 && x < pixels[0].length) {
                    pixels[y][x] = 1;
                }
            }
        }
    }
}
