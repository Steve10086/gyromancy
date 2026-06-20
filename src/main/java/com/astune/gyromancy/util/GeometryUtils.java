package com.astune.gyromancy.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Static geometric computation utilities for the symbol recognition engine.
 *
 * <p>Core matcher: Fourier Descriptors of contour.
 * Trace boundary, DFT, take coefficient magnitudes — rotation/scale/start-point invariant.
 */
public final class GeometryUtils {

    private GeometryUtils() {}

    // ═══════════════════════════════════════════════════════════════
    // Fourier Descriptors
    // ═══════════════════════════════════════════════════════════════

    private static final int FD_COEFFS = 8;   // keep first 8 AC coefficients
    private static final int FD_SAMPLES = 64; // resample contour to 64 equal-arc points

    /** 2D complex number (x + i*y) */
    private record Complex(double re, double im) {}

    /**
     * Computes Fourier Descriptor for a binary image.
     * Steps: trace boundary → resample → DFT → take magnitudes of first K AC coefficients,
     * normalised by |F[1]| for scale invariance.
     *
     * @return float[FD_COEFFS-1] descriptor (|F[2..8]| / |F[1]|)
     */
    public static float[] fourierDescriptor(int[][] binary) {
        List<int[]> contour = traceBoundary(binary);
        int n = contour.size();
        if (n < 4) return new float[FD_COEFFS - 1]; // too small, return zeros

        // Resample to equal arc-length
        List<Complex> pts = new ArrayList<>(FD_SAMPLES);
        for (int i = 0; i < FD_SAMPLES; i++) {
            double t = (double) i / FD_SAMPLES * n;
            int idx = (int) t;
            double frac = t - idx;
            int[] a = contour.get(idx % n);
            int[] b = contour.get((idx + 1) % n);
            pts.add(new Complex(a[0] + frac * (b[0] - a[0]), a[1] + frac * (b[1] - a[1])));
        }

        // Compute centroid and subtract for translation invariance
        double cx = 0, cy = 0;
        for (Complex p : pts) { cx += p.re; cy += p.im; }
        cx /= FD_SAMPLES; cy /= FD_SAMPLES;

        // DFT (direct, O(K*N) with K=8 N=64 → 512 multiplications, negligible)
        double[] real = new double[FD_COEFFS], imag = new double[FD_COEFFS];
        for (int k = 0; k < FD_COEFFS; k++) {
            for (int i = 0; i < FD_SAMPLES; i++) {
                double angle = -2.0 * Math.PI * k * i / FD_SAMPLES;
                double dx = pts.get(i).re - cx;
                double dy = pts.get(i).im - cy;
                real[k] += dx * Math.cos(angle) - dy * Math.sin(angle);
                imag[k] += dx * Math.sin(angle) + dy * Math.cos(angle);
            }
        }

        // Scale normalisation: divide by |F[1]|
        double f1 = Math.sqrt(real[1] * real[1] + imag[1] * imag[1]);
        if (f1 < 1e-6) return new float[FD_COEFFS - 1];

        // Output: |F[2..K]| / |F[1]|  (skip F[0]=DC, F[1]=scale ref)
        float[] desc = new float[FD_COEFFS - 1];
        for (int k = 2; k < FD_COEFFS; k++) {
            desc[k - 2] = (float) (Math.sqrt(real[k] * real[k] + imag[k] * imag[k]) / f1);
        }
        return desc;
    }

    /** L2 distance between two Fourier Descriptors */
    public static float fdDistance(float[] a, float[] b) {
        double sum = 0;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) { double d = a[i] - b[i]; sum += d * d; }
        return (float) Math.sqrt(sum);
    }

    /** Convert L2 FD distance to 0–1 similarity */
    public static float fdScore(float distance) {
        return 1.0f / (1.0f + distance);
    }

    // ═══════════════════════════════════════════════════════════════
    // Boundary tracing (Moore neighbor, 8-connected)
    // ═══════════════════════════════════════════════════════════════

    /** Returns ordered list of (x,y) contour points for the outermost shape in the image */
    static List<int[]> traceBoundary(int[][] binary) {
        int h = binary.length, w = h > 0 ? binary[0].length : 0;

        // Find first foreground pixel
        int sx = -1, sy = -1;
        outer:
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (binary[y][x] != 0) { sx = x; sy = y; break outer; }
        if (sx < 0) return List.of();

        // Moore neighbor tracing
        int[] dx8 = {1, 1, 0, -1, -1, -1, 0, 1};  // E, SE, S, SW, W, NW, N, NE
        int[] dy8 = {0, 1, 1, 1, 0, -1, -1, -1};

        // Find first boundary edge: background neighbor of starting pixel (search from W clockwise)
        int dir = 4; // start looking westward
        int bx = sx, by = sy, bdir = -1;
        for (int d = 0; d < 8; d++) {
            int nd = (dir + d) % 8;
            int nx = sx + dx8[nd], ny = sy + dy8[nd];
            if (nx < 0 || nx >= w || ny < 0 || ny >= h || binary[ny][nx] == 0) {
                bx = sx + dx8[nd]; by = sy + dy8[nd]; bdir = nd; break;
            }
        }
        if (bdir < 0) return List.of(new int[]{sx, sy}); // isolated pixel

        // Walk the boundary
        int cx = sx, cy = sy, entryDir = bdir;
        List<int[]> contour = new ArrayList<>();
        int maxSteps = w * h * 4; // safety

        while (maxSteps-- > 0) {
            contour.add(new int[]{cx, cy});
            // Search from entry+1 clockwise for next foreground pixel
            boolean found = false;
            for (int d = 1; d <= 8; d++) {
                int nd = (entryDir + d) % 8;
                int nx = cx + dx8[nd], ny = cy + dy8[nd];
                if (nx >= 0 && nx < w && ny >= 0 && ny < h && binary[ny][nx] != 0) {
                    cx = nx; cy = ny; entryDir = (nd + 4) % 8; // entry from opposite direction
                    found = true; break;
                }
            }
            if (!found) break;
            if (cx == sx && cy == sy) break; // closed loop
        }
        if (contour.isEmpty()) contour.add(new int[]{sx, sy});
        return contour;
    }

    // ═══════════════════════════════════════════════════════════════
    // Distance Transform & Chamfer Score (kept as debug / fallback)
    // ═══════════════════════════════════════════════════════════════

    private static final float D1 = 1.0f;
    private static final float D2 = 1.4142135f;

    public static float[][] distanceTransform(int[][] binary) {
        int h = binary.length;
        int w = h > 0 ? binary[0].length : 0;
        float[][] dt = new float[h][w];
        float INF = Float.MAX_VALUE * 0.5f;

        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                dt[y][x] = (binary[y][x] == 0) ? 0f : INF;

        for (int y = 1; y < h; y++) {
            for (int x = 1; x < w; x++) {
                if (dt[y][x] == 0f) continue;
                float nd = dt[y][x];
                nd = Math.min(nd, D1 + dt[y][x - 1]);
                nd = Math.min(nd, D2 + dt[y - 1][x - 1]);
                nd = Math.min(nd, D1 + dt[y - 1][x]);
                if (x + 1 < w) nd = Math.min(nd, D2 + dt[y - 1][x + 1]);
                dt[y][x] = nd;
            }
        }
        for (int y = h - 2; y >= 0; y--) {
            for (int x = w - 2; x >= 0; x--) {
                if (dt[y][x] == 0f) continue;
                float nd = dt[y][x];
                nd = Math.min(nd, D1 + dt[y][x + 1]);
                nd = Math.min(nd, D2 + dt[y + 1][x + 1]);
                nd = Math.min(nd, D1 + dt[y + 1][x]);
                if (x > 0) nd = Math.min(nd, D2 + dt[y + 1][x - 1]);
                dt[y][x] = nd;
            }
        }
        return dt;
    }

    public static float chamferScore(int[][] A, int[][] B) {
        int h = A.length, w = A[0].length;
        int[][] invA = new int[h][w], invB = new int[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                invA[y][x] = (A[y][x] == 0) ? 1 : 0;
                invB[y][x] = (B[y][x] == 0) ? 1 : 0;
            }
        float[][] dtA = distanceTransform(invA);
        float[][] dtB = distanceTransform(invB);
        double sum = 0;
        int countA = 0, countB = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                if (A[y][x] != 0) { sum += dtB[y][x]; countA++; }
                if (B[y][x] != 0) { sum += dtA[y][x]; countB++; }
            }
        int total = countA + countB;
        if (total == 0) return 1f;
        return (float) (1.0 / (1.0 + sum / total));
    }

    // ═══════════════════════════════════════════════════════════════
    // Normalization
    // ═══════════════════════════════════════════════════════════════

    public static int[][] normalize(int[][] source, int targetW, int targetH) {
        int sh = source.length;
        int sw = sh > 0 ? source[0].length : 0;
        if (sw == 0 || sh == 0) return new int[targetH][targetW];

        double[] centroid = computeCentroid(source);
        int[] bbox = computeBoundingBox(source);
        int bboxW = bbox[2] - bbox[0] + 1;
        int bboxH = bbox[3] - bbox[1] + 1;
        if (bboxW <= 0 || bboxH <= 0) return new int[targetH][targetW];

        double cx = centroid[0], cy = centroid[1];
        double padFactor = 0.1;
        double halfW = Math.max(bboxW / 2.0 * (1 + padFactor), 1);
        double halfH = Math.max(bboxH / 2.0 * (1 + padFactor), 1);
        double halfSize = Math.max(halfW, halfH);
        halfW = halfH = halfSize;
        double srcMinX = cx - halfW;
        double srcMinY = cy - halfH;

        int[][] result = new int[targetH][targetW];
        for (int ty = 0; ty < targetH; ty++) {
            for (int tx = 0; tx < targetW; tx++) {
                double sx = srcMinX + (tx + 0.5) * (2 * halfW) / targetW;
                double sy = srcMinY + (ty + 0.5) * (2 * halfH) / targetH;
                int isx = (int) Math.floor(sx), isy = (int) Math.floor(sy);
                int count = 0;
                if (isy >= 0 && isy < sh && isx >= 0 && isx < sw && source[isy][isx] != 0) count++;
                if (isy >= 0 && isy < sh && isx + 1 >= 0 && isx + 1 < sw && source[isy][isx + 1] != 0) count++;
                if (isy + 1 >= 0 && isy + 1 < sh && isx >= 0 && isx < sw && source[isy + 1][isx] != 0) count++;
                if (isy + 1 >= 0 && isy + 1 < sh && isx + 1 >= 0 && isx + 1 < sw && source[isy + 1][isx + 1] != 0) count++;
                result[ty][tx] = count >= 1 ? 1 : 0;
            }
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // Geometric properties
    // ═══════════════════════════════════════════════════════════════

    public static int[] computeBoundingBox(int[][] binaryImage) {
        int h = binaryImage.length, w = h > 0 ? binaryImage[0].length : 0;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (binaryImage[y][x] != 0) {
                    if (x < minX) minX = x; if (y < minY) minY = y;
                    if (x > maxX) maxX = x; if (y > maxY) maxY = y;
                }
        if (minX == Integer.MAX_VALUE) return new int[]{0, 0, 0, 0};
        return new int[]{minX, minY, maxX, maxY};
    }

    public static double[] computeCentroid(int[][] binaryImage) {
        int h = binaryImage.length, w = h > 0 ? binaryImage[0].length : 0;
        double totalMass = 0, cx = 0, cy = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int val = binaryImage[y][x];
                if (val != 0) { totalMass += val; cx += x * val; cy += y * val; }
            }
        if (totalMass == 0) return new double[]{0, 0};
        return new double[]{cx / totalMass, cy / totalMass};
    }

    public static int computeArea(int[][] binaryImage) {
        int area = 0;
        for (int[] row : binaryImage)
            for (int val : row)
                if (val != 0) area++;
        return area;
    }

    public static int[][] mirrorImage(int[][] image) {
        int h = image.length, w = h > 0 ? image[0].length : 0;
        int[][] mirrored = new int[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                mirrored[y][w - 1 - x] = image[y][x];
        return mirrored;
    }
}
