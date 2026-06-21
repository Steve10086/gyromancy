package com.astune.gyromancy.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Static geometric computation utilities for the symbol recognition engine.
 *
 * <p>Core matcher: SSIM (Structural Similarity) with dual PCA rotation.
 */
public final class GeometryUtils {

    private GeometryUtils() {}

    // ═══════════════════════════════════════════════════════════════
    // SSIM (Structural Similarity Index)
    // ═══════════════════════════════════════════════════════════════

    private static final float C1 = 0.0001f;
    private static final float C2 = 0.0009f;

    /**
     * Whole-image SSIM between two 32×32 binary images.
     * Mean, variance, and covariance are computed over all foreground+background pixels.
     */
    public static float ssim(int[][] A, int[][] B) {
        int h = A.length, w = A[0].length;
        int n = w * h;
        double sumA = 0, sumB = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                sumA += A[y][x]; sumB += B[y][x];
            }
        double muA = sumA / n, muB = sumB / n;

        double varA = 0, varB = 0, cov = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double da = A[y][x] - muA, db = B[y][x] - muB;
                varA += da * da; varB += db * db; cov += da * db;
            }
        varA /= n; varB /= n; cov /= n;

        double num = (2 * muA * muB + C1) * (2 * cov + C2);
        double den = (muA * muA + muB * muB + C1) * (varA + varB + C2);
        return (float) (num / den);
    }

    // ═══════════════════════════════════════════════════════════════
    // PCA (Principal Component Analysis)
    // ═══════════════════════════════════════════════════════════════

    /** Result of PCA on foreground pixel distribution */
    public record PCAResult(float angleDegrees, double eigenvalue1, double eigenvalue2) {}

    public static PCAResult computePCA(int[][] binary) {
        int h = binary.length, w = h > 0 ? binary[0].length : 0;
        List<Double> xs = new ArrayList<>(), ys = new ArrayList<>();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (binary[y][x] != 0) { xs.add((double) x); ys.add((double) y); }
        int n = xs.size();
        if (n < 2) return new PCAResult(0f, 0, 0);

        double mx = 0, my = 0;
        for (int i = 0; i < n; i++) { mx += xs.get(i); my += ys.get(i); }
        mx /= n; my /= n;

        double cxx = 0, cyy = 0, cxy = 0;
        for (int i = 0; i < n; i++) {
            double dx = xs.get(i) - mx, dy = ys.get(i) - my;
            cxx += dx * dx; cyy += dy * dy; cxy += dx * dy;
        }
        cxx /= n; cyy /= n; cxy /= n;

        double trace = cxx + cyy;
        double det = cxx * cyy - cxy * cxy;
        double disc = Math.sqrt(Math.max(0, trace * trace / 4 - det));
        double l1 = trace / 2 + disc, l2 = trace / 2 - disc;
        double rad = 0.5 * Math.atan2(2 * cxy, cxx - cyy);
        float deg = (float) Math.toDegrees(rad);
        if (deg < 0) deg += 180f;
        return new PCAResult(deg, l1, l2);
    }

    // ═══════════════════════════════════════════════════════════════
    // Contour tracing & Turning Function
    // ═══════════════════════════════════════════════════════════════

    /** Ordered list of contour pixels from 8-connected boundary trace */
    public record Contour(List<int[]> points, int length) {}

    private static final int[][] DIRS8 = {{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1},{0,-1},{1,-1}};

    /** Upscale factor for anti-aliased contour tracing */
    private static final int UPSCALE = 4; // 32→128

    /**
     * Upscales a binary image by replicating pixels (nearest-neighbor).
     */
    private static int[][] upscale(int[][] src, int factor) {
        int sh = src.length, sw = sh > 0 ? src[0].length : 0;
        int[][] dst = new int[sh * factor][sw * factor];
        for (int y = 0; y < sh; y++)
            for (int x = 0; x < sw; x++)
                if (src[y][x] != 0)
                    for (int dy = 0; dy < factor; dy++)
                        for (int dx = 0; dx < factor; dx++)
                            dst[y * factor + dy][x * factor + dx] = 1;
        return dst;
    }

    // ═══════════════════════ Zhang-Suen Thinning ═══════════════════

    /**
     * Zhang-Suen skeletonization — thins binary image to 1px wide lines.
     * Returns a new array; input is not modified.
     */
    public static int[][] thin(int[][] src) {
        int h = src.length, w = h > 0 ? src[0].length : 0;
        if (w == 0) return new int[0][0];
        boolean[][] img = new boolean[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                img[y][x] = src[y][x] != 0;

        boolean changed;
        do {
            changed = false;
            // Sub-iteration 1: delete SE-boundary and NW-corner pixels
            boolean[][] toRemove = new boolean[h][w];
            for (int y = 1; y < h - 1; y++)
                for (int x = 1; x < w - 1; x++)
                    if (img[y][x] && thinPass1(img, x, y))
                        toRemove[y][x] = changed = true;
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    if (toRemove[y][x]) img[y][x] = false;

            // Sub-iteration 2: delete NW-boundary and SE-corner pixels
            toRemove = new boolean[h][w];
            for (int y = 1; y < h - 1; y++)
                for (int x = 1; x < w - 1; x++)
                    if (img[y][x] && thinPass2(img, x, y))
                        toRemove[y][x] = changed = true;
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    if (toRemove[y][x]) img[y][x] = false;
        } while (changed);

        int[][] out = new int[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (img[y][x]) out[y][x] = 1;
        return out;
    }

    private static boolean thinPass1(boolean[][] img, int x, int y) {
        int p2 = bool(img[y-1][x]), p3 = bool(img[y-1][x+1]), p4 = bool(img[y][x+1]);
        int p5 = bool(img[y+1][x+1]), p6 = bool(img[y+1][x]), p7 = bool(img[y+1][x-1]);
        int p8 = bool(img[y][x-1]), p9 = bool(img[y-1][x-1]);
        int b = nnz(p2,p3,p4,p5,p6,p7,p8,p9);
        if (b < 2 || b > 6) return false;
        int a = trans(p2,p3,p4,p5,p6,p7,p8,p9);
        if (a != 1) return false;
        return p2 * p4 * p6 == 0 && p4 * p6 * p8 == 0;
    }

    private static boolean thinPass2(boolean[][] img, int x, int y) {
        int p2 = bool(img[y-1][x]), p3 = bool(img[y-1][x+1]), p4 = bool(img[y][x+1]);
        int p5 = bool(img[y+1][x+1]), p6 = bool(img[y+1][x]), p7 = bool(img[y+1][x-1]);
        int p8 = bool(img[y][x-1]), p9 = bool(img[y-1][x-1]);
        int b = nnz(p2,p3,p4,p5,p6,p7,p8,p9);
        if (b < 2 || b > 6) return false;
        int a = trans(p2,p3,p4,p5,p6,p7,p8,p9);
        if (a != 1) return false;
        return p2 * p4 * p8 == 0 && p2 * p6 * p8 == 0;
    }

    private static int bool(boolean v) { return v ? 1 : 0; }
    private static int nnz(int... vals) { int s=0; for(int v:vals)s+=v; return s; }
    private static int trans(int... p) {
        int t = 0;
        int[] v = {p[0],p[1],p[2],p[3],p[4],p[5],p[6],p[7],p[0]};
        for(int i=0;i<8;i++) if(v[i]==0&&v[i+1]==1) t++;
        return t;
    }

    // ═══════════════════ Corner Detection ═══════════════════

    /** A detected corner on a contour */
    public record Corner(int idx, int x, int y, double curvature, double angle) {}

    /** Gaussian kernel values for σ=1,2,4,8 (precomputed) */
    private static final int[] CORNER_SIGMAS = {1, 2, 4, 8};
    private static final double CORNER_THRESHOLD = 0.3;

    /**
     * CSS (Curvature Scale Space) corner detection.
     * Returns corners that survive at ≥2 smoothing scales.
     */
    public static List<Corner> detectCorners(Contour contour, int upscaleFactor) {
        int n = contour.length();
        if (n < 10) return List.of();

        // Precompute tangent angles at each point
        double[] tangents = new double[n];
        for (int i = 0; i < n; i++) {
            int i1 = (i + 1) % n;
            int[] a = contour.points().get(i);
            int[] b = contour.points().get(i1);
            tangents[i] = Math.atan2(b[1] - a[1], b[0] - a[0]);
        }

        // Compute curvature at each scale
        int numScales = CORNER_SIGMAS.length;
        double[][] curvatures = new double[numScales][n];

        for (int s = 0; s < numScales; s++) {
            int sigma = CORNER_SIGMAS[s];
            double[] smoothed = smoothTangents(tangents, sigma);
            for (int i = 0; i < n; i++) {
                int i1 = (i + 1) % n;
                double diff = smoothed[i1] - smoothed[i];
                while (diff > Math.PI) diff -= 2 * Math.PI;
                while (diff < -Math.PI) diff += 2 * Math.PI;
                curvatures[s][i] = Math.abs(diff);
            }
        }

        // Normalize each scale independently
        for (int s = 0; s < numScales; s++) {
            double maxC = 0;
            for (int i = 0; i < n; i++)
                if (curvatures[s][i] > maxC) maxC = curvatures[s][i];
            if (maxC > 1e-9)
                for (int i = 0; i < n; i++)
                    curvatures[s][i] /= maxC;
        }

        // Find local maxima that survive non-max suppression and cross-scale
        boolean[] isCorner = new boolean[n];
        for (int s = 0; s < numScales; s++) {
            boolean[] thisScale = new boolean[n];
            for (int i = 0; i < n; i++) {
                if (curvatures[s][i] < CORNER_THRESHOLD) continue;
                int im1 = (i - 1 + n) % n, ip1 = (i + 1) % n;
                if (curvatures[s][i] >= curvatures[s][im1]
                        && curvatures[s][i] >= curvatures[s][ip1]) {
                    thisScale[i] = true;
                }
            }
            // Suppress neighbors (2-point radius)
            for (int i = 0; i < n; i++)
                if (thisScale[i])
                    for (int d = -3; d <= 3; d++)
                        if (d != 0 && thisScale[(i + d + n) % n] && d < 0)
                            thisScale[i] = false;

            // Accumulate cross-scale
            for (int i = 0; i < n; i++)
                if (thisScale[i]) { /* corners need >=2 scales */ }
            // Simple approach: mark this scale's corners
            int scaleCount = 0;
            for (int i = 0; i < n; i++)
                if (thisScale[i]) scaleCount++;
        }

        // Cross-scale: a corner must appear in ≥2 scales
        int[] cornerVotes = new int[n];
        for (int s = 0; s < numScales; s++) {
            // Find local maxima for this scale
            boolean[] local = new boolean[n];
            for (int i = 0; i < n; i++) {
                if (curvatures[s][i] < CORNER_THRESHOLD) continue;
                int im1 = (i - 1 + n) % n, ip1 = (i + 1) % n;
                if (curvatures[s][i] >= curvatures[s][im1]
                        && curvatures[s][i] >= curvatures[s][ip1])
                    local[i] = true;
            }
            // Non-max suppression within radius 3
            for (int i = 0; i < n; i++) {
                if (!local[i]) continue;
                boolean suppressed = false;
                for (int d = -3; d <= 3; d++) {
                    if (d == 0) continue;
                    int ni = (i + d + n) % n;
                    if (local[ni] && curvatures[s][i] < curvatures[s][ni]) {
                        suppressed = true; break;
                    }
                }
                if (!suppressed) cornerVotes[i]++;
            }
        }

        // Corners with ≥2 scale votes
        List<Corner> corners = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (cornerVotes[i] >= 2 && !isSurpressed(i, cornerVotes, n)) {
                int[] p = contour.points().get(i);
                double maxCurvature = 0, bestAngle = 0;
                for (int s = 0; s < numScales; s++) {
                    if (curvatures[s][i] > maxCurvature) {
                        maxCurvature = curvatures[s][i];
                        bestAngle = tangents[i];
                    }
                }
                corners.add(new Corner(i, p[0] / upscaleFactor, p[1] / upscaleFactor,
                        maxCurvature, bestAngle));
            }
        }
        return corners;
    }

    private static double[] smoothTangents(double[] tangents, int sigma) {
        int n = tangents.length;
        double[] out = new double[n];
        // Gaussian smoothing on circular array
        double[] kernel = gaussKernel(sigma);
        int k = kernel.length;
        for (int i = 0; i < n; i++) {
            double sum = 0, wSum = 0;
            for (int j = 0; j < k; j++) {
                int idx = (i + j - k / 2 + n) % n;
                // Handle angle wrapping
                double diff = tangents[idx] - tangents[i];
                while (diff > Math.PI) diff -= 2 * Math.PI;
                while (diff < -Math.PI) diff += 2 * Math.PI;
                sum += (tangents[i] + diff) * kernel[j];
                wSum += kernel[j];
            }
            out[i] = sum / wSum;
            while (out[i] > Math.PI) out[i] -= 2 * Math.PI;
            while (out[i] < -Math.PI) out[i] += 2 * Math.PI;
        }
        return out;
    }

    private static double[] gaussKernel(int sigma) {
        int r = sigma * 3;
        double[] k = new double[2 * r + 1];
        double s2 = 2.0 * sigma * sigma;
        double sum = 0;
        for (int i = -r; i <= r; i++) {
            double w = Math.exp(-i * i / s2);
            k[i + r] = w;
            sum += w;
        }
        for (int i = 0; i < k.length; i++) k[i] /= sum;
        return k;
    }

    private static boolean isSurpressed(int i, int[] votes, int n) {
        if (votes[i] < 2) return true;
        for (int d = -2; d <= 2; d++) {
            if (d == 0) continue;
            int ni = (i + d + n) % n;
            if (votes[ni] > votes[i]) return true;
        }
        return false;
    }

    /**
     * Traces an 8-connected contour from the first non-zero pixel,
     * using Moore neighborhood tracing on a 4× upscaled grid
     * to reduce staircase artifacts.
     */
    public static Contour traceContour(int[][] binary) {
        // Upscale to smooth jagged edges (no thinning — TF needs thick contours)
        int[][] up = upscale(binary, UPSCALE);
        return traceContourRaw(up, UPSCALE);
    }

    /** Contour from a thinned skeleton (for corner detection only) */
    public static Contour traceThinnedContour(int[][] binary) {
        int[][] up = upscale(binary, UPSCALE);
        int[][] skel = thin(up);
        return traceContourRaw(skel, UPSCALE);
    }

    private static Contour traceContourRaw(int[][] grid, int factor) {
        int h = grid.length, w = h > 0 ? grid[0].length : 0;
        if (w == 0) return new Contour(List.of(), 0);

        int sx = -1, sy = -1;
        outer:
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (grid[y][x] != 0) { sx = x; sy = y; break outer; }
        if (sx < 0) return new Contour(List.of(), 0);

        boolean[][] visited = new boolean[h][w];
        List<int[]> points = new ArrayList<>();
        int cx = sx, cy = sy;
        int prevDir = 0;

        do {
            points.add(new int[]{cx, cy});
            visited[cy][cx] = true;

            boolean found = false;
            for (int d = 0; d < 8; d++) {
                int dir = (prevDir + 6 + d) % 8;
                int nx = cx + DIRS8[dir][0], ny = cy + DIRS8[dir][1];
                if (nx >= 0 && nx < w && ny >= 0 && ny < h && grid[ny][nx] != 0) {
                    cx = nx; cy = ny;
                    prevDir = dir;
                    found = true;
                    break;
                }
            }
            if (!found) break;
        } while (!(cx == sx && cy == sy) && points.size() < w * h);

        return new Contour(points, points.size());
    }

    /**
     * Computes the normalized turning function Θ(s) for a contour.
     *
     * <p>The turning function is the cumulative tangent angle as a function
     * of normalized arc length. It is rotation-invariant under cyclic shift
     * and scale-invariant due to arc-length normalization.
     *
     * @param contour the traced contour
     * @param M       number of equal-arc-length samples (e.g. 72)
     * @return turning function values Θ[0..M-1], each in radians
     */
    public static double[] turningFunction(Contour contour, int M) {
        List<int[]> pts = contour.points();
        int n = pts.size();
        if (n < 2) return new double[M];

        // Compute cumulative arc length
        double[] cumLen = new double[n];
        cumLen[0] = 0;
        for (int i = 1; i < n; i++) {
            int[] a = pts.get(i - 1), b = pts.get(i);
            double dx = b[0] - a[0], dy = b[1] - a[1];
            cumLen[i] = cumLen[i - 1] + Math.sqrt(dx * dx + dy * dy);
        }
        // Close the loop
        int[] a = pts.get(n - 1), b = pts.get(0);
        double dx = b[0] - a[0], dy = b[1] - a[1];
        double totalLen = cumLen[n - 1] + Math.sqrt(dx * dx + dy * dy);

        if (totalLen < 1e-6) return new double[M];

        // Compute turning angles at each contour point
        double[] turns = new double[n];
        for (int i = 0; i < n; i++) {
            int[] prev = pts.get((i - 1 + n) % n);
            int[] curr = pts.get(i);
            int[] next = pts.get((i + 1) % n);
            double inDx = curr[0] - prev[0], inDy = curr[1] - prev[1];
            double outDx = next[0] - curr[0], outDy = next[1] - curr[1];
            double inAngle = Math.atan2(inDy, inDx);
            double outAngle = Math.atan2(outDy, outDx);
            double turn = outAngle - inAngle;
            // Normalize to [-π, π]
            while (turn > Math.PI) turn -= 2 * Math.PI;
            while (turn < -Math.PI) turn += 2 * Math.PI;
            turns[i] = turn;
        }

        // Build cumulative turning function Θ as a function of arc length
        double[] cumTurn = new double[n];
        cumTurn[0] = 0;
        for (int i = 1; i < n; i++) {
            cumTurn[i] = cumTurn[i - 1] + turns[i];
        }

        // Resample at M equal arc-length intervals
        double[] tf = new double[M];
        int segIdx = 0;
        for (int k = 0; k < M; k++) {
            double targetLen = (k + 0.5) * totalLen / M;
            // Find the segment containing targetLen
            while (segIdx < n - 1 && cumLen[segIdx + 1] < targetLen) segIdx++;
            double segStart = cumLen[segIdx];
            double segEnd = (segIdx < n - 1) ? cumLen[segIdx + 1] : totalLen;
            double segLen = segEnd - segStart;
            double t = segLen > 1e-9 ? (targetLen - segStart) / segLen : 0;
            t = Math.clamp(t, 0, 1);

            // Linear interpolation of cumulative turning angle
            double nextTurn = (segIdx < n - 1) ? cumTurn[segIdx + 1] : cumTurn[0] + turns[0];
            tf[k] = cumTurn[segIdx] + t * (nextTurn - cumTurn[segIdx]);
        }

        return tf;
    }

    // ═══════════════════ Dynamic Time Warping ═══════════════════

    /**
     * Cyclic DTW distance between two sequences of equal length.
     *
     * <p>Uses Dynamic Time Warping with Sakoe-Chiba band constraint
     * for elastic matching. Tries all cyclic shifts of seq2 to handle
     * rotation, and returns the minimum normalized DTW distance.
     *
     * @param seq1 first sequence (length M)
     * @param seq2 second sequence (length M)
     * @param bandWidth Sakoe-Chiba band half-width (e.g. 10)
     * @return minimum DTW distance, normalized by path length
     */
    public static double cyclicDtwDistance(double[] seq1, double[] seq2, int bandWidth) {
        int M = seq1.length;
        if (M != seq2.length) return Double.MAX_VALUE;
        if (M == 0) return 0;

        double bestDist = Double.MAX_VALUE;

        // Try each cyclic shift of seq2
        for (int shift = 0; shift < M; shift++) {
            double dist = dtwDistance(seq1, seq2, shift, bandWidth);
            if (dist < bestDist) bestDist = dist;
        }

        return bestDist;
    }

    /**
     * Standard DTW with Sakoe-Chiba band between seq1 and cyclically
     * shifted seq2. Returns distance normalized by path length.
     */
    private static double dtwDistance(double[] seq1, double[] seq2, int shift, int bandWidth) {
        int M = seq1.length;
        // DTW matrix: rows=seq1 (0..M), cols=seq2 (0..M) — but we only need
        // a band around the diagonal for Sakoe-Chiba constraint.
        // We use two rows for memory efficiency.

        double[] prevRow = new double[M + 1];
        double[] currRow = new double[M + 1];

        // Initialize with infinity
        for (int j = 0; j <= M; j++) prevRow[j] = Double.MAX_VALUE;
        prevRow[0] = 0;

        for (int i = 1; i <= M; i++) {
            currRow[0] = Double.MAX_VALUE;
            int jStart = Math.max(1, i - bandWidth);
            int jEnd = Math.min(M, i + bandWidth);

            for (int j = 1; j <= M; j++) {
                if (j < jStart || j > jEnd) {
                    currRow[j] = Double.MAX_VALUE;
                    continue;
                }

                int j2 = (j - 1 + shift) % M;
                double cost = Math.abs(seq1[i - 1] - seq2[j2]);

                double minPrev = Math.min(prevRow[j], currRow[j - 1]);
                minPrev = Math.min(minPrev, prevRow[j - 1]);

                currRow[j] = cost + minPrev;
            }

            // Swap rows
            double[] tmp = prevRow;
            prevRow = currRow;
            currRow = tmp;
        }

        // Normalize by M (sequence length) for scale-invariant comparison
        double totalDist = prevRow[M];
        if (totalDist == Double.MAX_VALUE) return Double.MAX_VALUE;
        return totalDist / M;
    }

    // ═══════════════════ Curvature Function ═══════════════════

    /**
     * Computes the curvature function κ(s) from a turning function Θ(s)
     * by discrete forward-difference differentiation.
     *
     * <p>κ(s) = dΘ/ds is non-cumulative: local contour noise does not
     * propagate to distant samples. The values are in radians per sample
     * step and are rotation-invariant under cyclic shift.
     *
     * @param tf turning function values Θ[0..M-1]
     * @return curvature values κ[0..M-1] (non-cumulative, radians/sample)
     */
    public static double[] curvatureFromTurningFunction(double[] tf) {
        return curvatureFromTurningFunction(tf, 0);
    }

    /**
     * Computes the curvature function κ(s) from a turning function Θ(s),
     * with optional Gaussian smoothing on the circular array.
     *
     * @param tf    turning function Θ[0..M-1]
     * @param sigma Gaussian sigma for smoothing (0 = no smoothing)
     * @return smoothed curvature values κ[0..M-1]
     */
    public static double[] curvatureFromTurningFunction(double[] tf, double sigma) {
        int M = tf.length;
        double[] curv = new double[M];
        for (int i = 0; i < M; i++) {
            curv[i] = tf[(i + 1) % M] - tf[i];
            // Wrap to [-π, π]
            while (curv[i] > Math.PI) curv[i] -= 2 * Math.PI;
            while (curv[i] < -Math.PI) curv[i] += 2 * Math.PI;
        }

        if (sigma <= 0) return curv;

        // Gaussian smoothing on circular array
        double[] kernel = gaussKernel((int) Math.round(sigma));
        int k = kernel.length;
        double[] smoothed = new double[M];
        for (int i = 0; i < M; i++) {
            double sum = 0, wSum = 0;
            for (int j = 0; j < k; j++) {
                int idx = (i + j - k / 2 + M) % M;
                // Handle angular wrapping for smoothing
                double diff = curv[idx] - curv[i];
                while (diff > Math.PI) diff -= 2 * Math.PI;
                while (diff < -Math.PI) diff += 2 * Math.PI;
                sum += (curv[i] + diff) * kernel[j];
                wSum += kernel[j];
            }
            smoothed[i] = sum / wSum;
        }
        return smoothed;
    }

    // ═══════════════════ Morphological Smoothing ═══════════════════

    /**
     * Majority-filter smoothing of a binary image.
     * For each pixel, sets it to 1 if ≥5 of its 3×3 neighbors are foreground.
     * This removes isolated noise and fills small gaps.
     */
    public static int[][] smoothBinary(int[][] src) {
        int h = src.length, w = h > 0 ? src[0].length : 0;
        if (w == 0) return new int[0][0];
        int[][] dst = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int count = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int ny = y + dy, nx = x + dx;
                        if (ny >= 0 && ny < h && nx >= 0 && nx < w && src[ny][nx] != 0)
                            count++;
                    }
                }
                dst[y][x] = count >= 5 ? 1 : 0;
            }
        }
        return dst;
    }

    // ═══════════════════ Decimation ═══════════════════

    /**
     * Decimates an array by averaging pairs of consecutive samples.
     * Acts as a low-pass filter — removes high-frequency noise.
     */
    public static double[] decimate(double[] arr, int targetLen) {
        int srcLen = arr.length;
        if (targetLen >= srcLen) return arr.clone();
        double[] out = new double[targetLen];
        double ratio = (double) srcLen / targetLen;
        for (int i = 0; i < targetLen; i++) {
            double srcIdx = i * ratio;
            int lo = (int) Math.floor(srcIdx);
            int hi = Math.min(lo + 1, srcLen - 1);
            double frac = srcIdx - lo;
            out[i] = arr[lo] * (1 - frac) + arr[hi] * frac;
        }
        return out;
    }

    // ═══════════════════ Curvature Emphasis ═══════════════════

    /**
     * Emphasizes large curvature values by applying f(x) = x * |x|
     * (preserving sign). This makes matching driven primarily by
     * major shape features (corners) rather than hand-drawn noise.
     *
     * @param curv raw curvature values
     * @return emphasized curvature values
     */
    public static double[] emphasizeCurvature(double[] curv) {
        double[] out = new double[curv.length];
        for (int i = 0; i < curv.length; i++)
            out[i] = curv[i] * Math.abs(curv[i]);
        return out;
    }

    // ═══════════════════ Centroid Distance Function ═══════════════════

    /**
     * Computes the centroid distance function r(s) for a contour.
     *
     * <p>r(s) = distance from each contour point to the contour's centroid,
     * resampled at M equal arc-length intervals and normalized by mean distance.
     *
     * <p>CDF is rotation-invariant under cyclic shift, scale-invariant after
     * mean normalization, and non-cumulative (local noise stays local).
     * It captures the radial profile of the shape — complementary to curvature.
     *
     * @param contour the traced contour
     * @param M       number of equal-arc-length samples
     * @return centroid distance values r[0..M-1], mean-normalized
     */
    public static double[] centroidDistanceFunction(Contour contour, int M) {
        List<int[]> pts = contour.points();
        int n = pts.size();
        if (n < 2) return new double[M];

        // Compute centroid of contour points
        double cx = 0, cy = 0;
        for (int[] p : pts) { cx += p[0]; cy += p[1]; }
        cx /= n; cy /= n;

        // Compute cumulative arc length and raw centroid distances
        double[] cumLen = new double[n];
        double[] rawDist = new double[n];
        cumLen[0] = 0;
        for (int i = 0; i < n; i++) {
            int[] p = pts.get(i);
            rawDist[i] = Math.sqrt((p[0] - cx) * (p[0] - cx) + (p[1] - cy) * (p[1] - cy));
            if (i > 0) {
                int[] a = pts.get(i - 1), b = pts.get(i);
                double dx = b[0] - a[0], dy = b[1] - a[1];
                cumLen[i] = cumLen[i - 1] + Math.sqrt(dx * dx + dy * dy);
            }
        }
        // Close the loop
        int[] a = pts.get(n - 1), b = pts.get(0);
        double dx = b[0] - a[0], dy = b[1] - a[1];
        double totalLen = cumLen[n - 1] + Math.sqrt(dx * dx + dy * dy);
        if (totalLen < 1e-6) return new double[M];

        // Compute mean distance for scale normalization
        double meanDist = 0;
        for (int i = 0; i < n; i++) meanDist += rawDist[i];
        meanDist /= n;
        if (meanDist < 1e-6) return new double[M];

        // Resample at M equal arc-length intervals
        double[] cdf = new double[M];
        int segIdx = 0;
        for (int k = 0; k < M; k++) {
            double targetLen = (k + 0.5) * totalLen / M;
            while (segIdx < n - 1 && cumLen[segIdx + 1] < targetLen) segIdx++;
            double segStart = cumLen[segIdx];
            double segEnd = (segIdx < n - 1) ? cumLen[segIdx + 1] : totalLen;
            double segLen = segEnd - segStart;
            double t = segLen > 1e-9 ? (targetLen - segStart) / segLen : 0;
            t = Math.clamp(t, 0, 1);
            double nextDist = (segIdx < n - 1) ? rawDist[segIdx + 1] : rawDist[0];
            cdf[k] = (rawDist[segIdx] + t * (nextDist - rawDist[segIdx])) / meanDist;
        }

        return cdf;
    }

    /**
     * L2 distance between two turning functions under optimal cyclic shift and mirror.
     *
     * <p>Turning functions are invariant to rotation under cyclic shift.
     * We use FFT-based circular cross-correlation for O(M log M) shift finding.
     *
     * @param tf1    turning function of drawn glyph
     * @param tf2    turning function of template
     * @param allowMirror whether to try reversed tf1
     * @return minimum L2 distance (lower = more similar)
     */
    public static double tfDistance(double[] tf1, double[] tf2, boolean allowMirror) {
        int M = tf1.length;
        if (M != tf2.length) return Double.MAX_VALUE;

        // Compute norm squares for fast L2 calculation
        double norm1 = 0, norm2 = 0;
        for (int i = 0; i < M; i++) { norm1 += tf1[i] * tf1[i]; norm2 += tf2[i] * tf2[i]; }

        // Cross-correlation via convolution: corr[shift] = Σ tf1[k] * tf2[(k+shift)%M]
        // L2(tf1_shifted, tf2)² = norm1 + norm2 - 2*corr[shift]
        double bestDist = Double.MAX_VALUE;

        for (int shift = 0; shift < M; shift++) {
            double corr = 0;
            for (int k = 0; k < M; k++) {
                corr += tf1[k] * tf2[(k + shift) % M];
            }
            double distSq = norm1 + norm2 - 2 * corr;
            if (distSq < bestDist) bestDist = distSq;
        }

        // Mirror: reverse tf1 and try again
        if (allowMirror) {
            double[] tf1Rev = new double[M];
            for (int i = 0; i < M; i++) tf1Rev[i] = tf1[M - 1 - i];
            for (int shift = 0; shift < M; shift++) {
                double corr = 0;
                for (int k = 0; k < M; k++) {
                    corr += tf1Rev[k] * tf2[(k + shift) % M];
                }
                double distSq = norm1 + norm2 - 2 * corr;
                if (distSq < bestDist) bestDist = distSq;
            }
        }

        return Math.sqrt(Math.max(0, bestDist)) / M;
    }

    // ═══════════════════════════════════════════════════════════════
    // Rotation
    // ═══════════════════════════════════════════════════════════════

    public static int[][] rotateImage(int[][] src, float degrees) {
        int h = src.length, w = h > 0 ? src[0].length : 0;
        if (w == 0) return new int[32][32];
        double rad = Math.toRadians(-degrees);
        double cos = Math.cos(rad), sin = Math.sin(rad);
        double cx = (w - 1) / 2.0, cy = (h - 1) / 2.0;
        int[][] out = new int[32][32];
        double ocx = 15.5, ocy = 15.5;
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 32; x++) {
                double sx = (x - ocx) * cos + (y - ocy) * sin + cx;
                double sy = -(x - ocx) * sin + (y - ocy) * cos + cy;
                int isx = (int) Math.round(sx), isy = (int) Math.round(sy);
                if (isx >= 0 && isx < w && isy >= 0 && isy < h && src[isy][isx] != 0)
                    out[y][x] = 1;
            }
        return out;
    }

    public static int[][] mirrorImage(int[][] image) {
        int h = image.length, w = h > 0 ? image[0].length : 0;
        int[][] m = new int[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                m[y][w - 1 - x] = image[y][x];
        return m;
    }

    // ═══════════════════════════════════════════════════════════════
    // Normalization & geometry
    // ═══════════════════════════════════════════════════════════════

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

}
