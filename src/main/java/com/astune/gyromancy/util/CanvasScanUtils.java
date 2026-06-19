package com.astune.gyromancy.util;

import com.astune.painter.api.IPixelMatrix;

import java.util.*;

/**
 * Canvas scanning utilities for single-face pixel analysis.
 * Used as a subroutine by the cross-block FloodFillExtractor for
 * within-face connected component analysis.
 */
public final class CanvasScanUtils {

    private CanvasScanUtils() {}

    /**
     * Represents a single connected component extracted from a canvas face.
     */
    public record ConnectedComponent(
            /** Extracted sub-image cropped to the bounding box */
            int[][] pixels,
            /** Bounding box top-left (original coordinates on face) */
            int minX, int minY,
            /** Bounding box bottom-right (original coordinates on face) */
            int maxX, int maxY,
            /** Center of mass (original coordinates) */
            double centroidX, double centroidY,
            /** Foreground pixel count */
            int area
    ) {}

    /**
     * Extracts all connected components using 8-connected BFS flood fill.
     *
     * @param matrix  the 16×16 canvas face pixel matrix (IPixelMatrix)
     * @param minArea minimum foreground pixel count for a component (filters noise)
     * @return list of connected components, sorted by area descending
     */
    public static List<ConnectedComponent> extractComponents(IPixelMatrix matrix, int minArea) {
        int w = matrix.getWidth();
        int h = matrix.getHeight();
        boolean[][] visited = new boolean[h][w];
        List<ConnectedComponent> components = new ArrayList<>();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (visited[y][x]) continue;
                if (matrix.getPixel(x, y) == 0) continue;

                // Start BFS for this component
                ConnectedComponent comp = floodFill(matrix, visited, x, y, w, h);
                if (comp != null && comp.area >= minArea) {
                    components.add(comp);
                }
            }
        }

        // Sort by area descending (largest first)
        components.sort((a, b) -> Integer.compare(b.area, a.area));
        return components;
    }

    /**
     * Convenience overload using IPixelMatrix.
     */
    public static List<ConnectedComponent> extractComponents(int[][] pixels, int minArea) {
        int h = pixels.length;
        int w = h > 0 ? pixels[0].length : 0;
        boolean[][] visited = new boolean[h][w];
        List<ConnectedComponent> components = new ArrayList<>();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (visited[y][x]) continue;
                if (pixels[y][x] == 0) continue;

                ConnectedComponent comp = floodFillRaw(pixels, visited, x, y, w, h);
                if (comp != null && comp.area >= minArea) {
                    components.add(comp);
                }
            }
        }

        components.sort((a, b) -> Integer.compare(b.area, a.area));
        return components;
    }

    /**
     * 8-connected BFS flood fill using IPixelMatrix.
     */
    private static ConnectedComponent floodFill(IPixelMatrix matrix, boolean[][] visited,
                                                 int startX, int startY, int w, int h) {
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startX, startY});
        visited[startY][startX] = true;

        int minX = startX, minY = startY, maxX = startX, maxY = startY;
        double sumX = 0, sumY = 0;
        int area = 0;

        // Record all pixel positions for sub-image extraction
        List<int[]> pixelPositions = new ArrayList<>();

        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            int px = p[0], py = p[1];

            if (matrix.getPixel(px, py) == 0) continue;

            area++;
            sumX += px;
            sumY += py;
            pixelPositions.add(new int[]{px, py});

            if (px < minX) minX = px;
            if (py < minY) minY = py;
            if (px > maxX) maxX = px;
            if (py > maxY) maxY = py;

            // 8-connected neighbors
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int nx = px + dx;
                    int ny = py + dy;
                    if (nx >= 0 && nx < w && ny >= 0 && ny < h && !visited[ny][nx]) {
                        visited[ny][nx] = true;
                        queue.add(new int[]{nx, ny});
                    }
                }
            }
        }

        if (area == 0) return null;

        // Extract sub-image cropped to bounding box
        int bw = maxX - minX + 1;
        int bh = maxY - minY + 1;
        int[][] subPixels = new int[bh][bw];
        for (int[] pos : pixelPositions) {
            subPixels[pos[1] - minY][pos[0] - minX] = 1;
        }

        double centroidX = sumX / area;
        double centroidY = sumY / area;

        return new ConnectedComponent(subPixels, minX, minY, maxX, maxY, centroidX, centroidY, area);
    }

    /**
     * 8-connected BFS flood fill using raw int[][].
     */
    private static ConnectedComponent floodFillRaw(int[][] pixels, boolean[][] visited,
                                                    int startX, int startY, int w, int h) {
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startX, startY});
        visited[startY][startX] = true;

        int minX = startX, minY = startY, maxX = startX, maxY = startY;
        double sumX = 0, sumY = 0;
        int area = 0;
        List<int[]> pixelPositions = new ArrayList<>();

        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            int px = p[0], py = p[1];

            if (pixels[py][px] == 0) continue;

            area++;
            sumX += px;
            sumY += py;
            pixelPositions.add(new int[]{px, py});

            if (px < minX) minX = px;
            if (py < minY) minY = py;
            if (px > maxX) maxX = px;
            if (py > maxY) maxY = py;

            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int nx = px + dx;
                    int ny = py + dy;
                    if (nx >= 0 && nx < w && ny >= 0 && ny < h && !visited[ny][nx]) {
                        visited[ny][nx] = true;
                        queue.add(new int[]{nx, ny});
                    }
                }
            }
        }

        if (area == 0) return null;

        int bw = maxX - minX + 1;
        int bh = maxY - minY + 1;
        int[][] subPixels = new int[bh][bw];
        for (int[] pos : pixelPositions) {
            subPixels[pos[1] - minY][pos[0] - minX] = 1;
        }

        return new ConnectedComponent(subPixels, minX, minY, maxX, maxY, sumX / area, sumY / area, area);
    }

    /**
     * Filters components by aspect ratio bounds.
     * @param maxAspectRatio maximum ratio of width/height or height/width
     */
    public static List<ConnectedComponent> filterByAspectRatio(
            List<ConnectedComponent> components, double maxAspectRatio) {
        List<ConnectedComponent> filtered = new ArrayList<>();
        for (ConnectedComponent comp : components) {
            int bw = comp.maxX - comp.minX + 1;
            int bh = comp.maxY - comp.minY + 1;
            if (bh == 0) continue;
            double aspect = (double) bw / bh;
            if (aspect <= maxAspectRatio && 1.0 / aspect <= maxAspectRatio) {
                filtered.add(comp);
            }
        }
        return filtered;
    }

    /**
     * Filters components by area bounds.
     */
    public static List<ConnectedComponent> filterByArea(
            List<ConnectedComponent> components, int minArea, int maxArea) {
        List<ConnectedComponent> filtered = new ArrayList<>();
        for (ConnectedComponent comp : components) {
            if (comp.area >= minArea && comp.area < maxArea) {
                filtered.add(comp);
            }
        }
        return filtered;
    }
}
