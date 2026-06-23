package com.astune.gyromancy.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    public static int[][] upscale(int[][] src, int factor) {
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

    /**
     * Fills enclosed background holes whose area is below
     * {@code max(5, max(w,h)/10)} using 4-connected flood fill.
     *
     * <p>A hole is a background (0) region that does not touch any border
     * of the image. Small holes from drawing artifacts (thin-line crossings,
     * missed corner fills) are filled to produce a solid shape before
     * skeletonization.
     *
     * @param src binary image (0=bg, 1=fg)
     * @return new image with small holes filled
     */
    public static int[][] fillSmallHoles(int[][] src) {
        int h = src.length, w = h > 0 ? src[0].length : 0;
        if (w == 0) return new int[0][0];

        int areaThreshold = Math.min(5, Math.max(w, h) / 10);

        // Copy src into mutable grid; we'll fill holes in-place on the copy
        int[][] dst = new int[h][w];
        for (int y = 0; y < h; y++) System.arraycopy(src[y], 0, dst[y], 0, w);

        // visited mask for flood fill (4-connected bg scan)
        boolean[][] visited = new boolean[h][w];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (dst[y][x] != 0 || visited[y][x]) continue;

                // Flood-fill this background region
                java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
                java.util.ArrayList<int[]> region = new java.util.ArrayList<>();
                q.add(new int[]{x, y});
                visited[y][x] = true;
                boolean touchesBorder = false;

                while (!q.isEmpty()) {
                    int[] p = q.poll();
                    int cx = p[0], cy = p[1];
                    region.add(p);

                    if (cx == 0 || cx == w - 1 || cy == 0 || cy == h - 1)
                        touchesBorder = true;

                    // 4-connected neighbors
                    if (cx > 0 && dst[cy][cx - 1] == 0 && !visited[cy][cx - 1]) {
                        visited[cy][cx - 1] = true; q.add(new int[]{cx - 1, cy});
                    }
                    if (cx + 1 < w && dst[cy][cx + 1] == 0 && !visited[cy][cx + 1]) {
                        visited[cy][cx + 1] = true; q.add(new int[]{cx + 1, cy});
                    }
                    if (cy > 0 && dst[cy - 1][cx] == 0 && !visited[cy - 1][cx]) {
                        visited[cy - 1][cx] = true; q.add(new int[]{cx, cy - 1});
                    }
                    if (cy + 1 < h && dst[cy + 1][cx] == 0 && !visited[cy + 1][cx]) {
                        visited[cy + 1][cx] = true; q.add(new int[]{cx, cy + 1});
                    }
                }

                // Fill if enclosed and small
                if (!touchesBorder && region.size() < areaThreshold) {
                    for (int[] p : region) dst[p[1]][p[0]] = 1;
                }
            }
        }

        return dst;
    }

    /**
     * Upscales a binary image to at least {@code minSize} in both dimensions
     * while <b>preserving 8-connectivity</b>.
     *
     * <p>Each original black pixel produces a K×K block, where
     * K = ⌈minSize / min(W,H)⌉. For every pair of diagonally-adjacent
     * original black pixels, two bridging pixels are added to convert the
     * corner-only touch into an edge connection (preserving 8-connectivity).
     *
     * <p>Without bridging, a 45° diagonal line in the original would become
     * blocks touching only at corners — Zhang-Suen then produces staircase
     * artifacts (dozens of false junctions). Bridging fixes this while
     * keeping line width tight (K pixels, not K+1).
     *
     * <p>Output dimensions: {@code W*K+1 × H*K+1} (single-pixel boundary
     * strip at right/bottom edges may exist when K doesn't divide evenly).
     *
     * @param src     binary image
     * @param minSize minimum output width and height (e.g. 128)
     * @return upscaled image with preserved 8-connectivity
     */
    public static int[][] upscaleConnectivityPreserving(int[][] src, int minSize) {
        int sh = src.length, sw = sh > 0 ? src[0].length : 0;
        if (sw == 0 || sh == 0) return new int[minSize][minSize];

        int minDim = Math.min(sw, sh);
        int K = (minSize + minDim - 1) / minDim; // ceil(minSize/minDim)
        if (K < 1) K = 1;

        int dw = sw * K + 1;
        int dh = sh * K + 1;
        int[][] dst = new int[dh][dw];

        // Phase 1: fill K×K blocks for each black source pixel
        for (int oy = 0; oy < sh; oy++) {
            int y0 = oy * K;
            for (int ox = 0; ox < sw; ox++) {
                if (src[oy][ox] == 0) continue;
                int x0 = ox * K;
                for (int dy = 0; dy < K; dy++)
                    for (int dx = 0; dx < K; dx++)
                        dst[y0 + dy][x0 + dx] = 1;
            }
        }

        // Phase 2: bridge diagonally-adjacent blocks
        // For each pair (ox,oy)-(ox+dx,oy+dy) both black with |dx|=|dy|=1,
        // fill the two pixels at the shared corner to make them edge-connected.
        for (int oy = 0; oy < sh; oy++) {
            for (int ox = 0; ox < sw; ox++) {
                if (src[oy][ox] == 0) continue;

                // SE diagonal: (ox,oy) and (ox+1,oy+1)
                if (ox + 1 < sw && oy + 1 < sh && src[oy + 1][ox + 1] != 0) {
                    int cx = ox * K + K - 1;  // right edge of (ox,oy) block
                    int cy = oy * K + K - 1;  // bottom edge of (ox,oy) block
                    dst[cy][cx + 1] = 1;      // right bridge
                    dst[cy + 1][cx] = 1;      // down bridge
                }
                // SW diagonal: (ox,oy) and (ox-1,oy+1)
                if (ox - 1 >= 0 && oy + 1 < sh && src[oy + 1][ox - 1] != 0) {
                    int cx = ox * K;            // left edge of (ox,oy) block
                    int cy = oy * K + K - 1;    // bottom edge of (ox,oy) block
                    dst[cy][cx - 1] = 1;        // left bridge
                    dst[cy + 1][cx] = 1;        // down bridge
                }
            }
        }

        return dst;
    }

    /**
     * Anti-alias a binary image by Gaussian blur + threshold.
     *
     * <p>Removes staircase artifacts from block-upscaled images: treats
     * the binary as a continuous field, applies a Gaussian kernel, then
     * thresholds back at 0.5. The result has smooth diagonal edges
     * instead of pixel-level steps.
     *
     * @param src   binary image (0=bg, 1=fg)
     * @param sigma Gaussian sigma in output pixels
     * @return anti-aliased binary image (same dimensions)
     */
    public static int[][] gaussianSmoothBinary(int[][] src, double sigma) {
        int h = src.length, w = h > 0 ? src[0].length : 0;
        if (w == 0 || sigma <= 0) {
            int[][] copy = new int[h][w];
            for (int y = 0; y < h; y++) System.arraycopy(src[y], 0, copy[y], 0, w);
            return copy;
        }

        // Float copy
        float[][] buf = new float[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                buf[y][x] = src[y][x];

        // 1D Gaussian kernel
        int r = (int) Math.ceil(3.0 * sigma);
        double[] kernel = new double[2 * r + 1];
        double s2 = 2.0 * sigma * sigma;
        double ksum = 0;
        for (int i = -r; i <= r; i++) {
            kernel[i + r] = Math.exp(-i * i / s2);
            ksum += kernel[i + r];
        }
        for (int i = 0; i < kernel.length; i++) kernel[i] /= ksum;

        // Horizontal pass
        float[][] hPass = new float[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double sum = 0;
                for (int i = -r; i <= r; i++) {
                    int sx = x + i;
                    if (sx < 0) sx = 0;
                    else if (sx >= w) sx = w - 1;
                    sum += buf[y][sx] * kernel[i + r];
                }
                hPass[y][x] = (float) sum;
            }
        }

        // Vertical pass + threshold
        int[][] dst = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double sum = 0;
                for (int i = -r; i <= r; i++) {
                    int sy = y + i;
                    if (sy < 0) sy = 0;
                    else if (sy >= h) sy = h - 1;
                    sum += hPass[sy][x] * kernel[i + r];
                }
                dst[y][x] = sum >= 0.5 ? 1 : 0;
            }
        }

        return dst;
    }

    // ═══════ Guo-Hall Thinning (mirrors skimage thin(), exact LUTs) ═══════

    /**
     * Neighborhood mask matching skimage {@code thin()}:
     * <pre>
     *   [ 8][ 4][ 2]     NW(bit3) N(bit2)  NE(bit1)
     *   [16][ 0][ 1]      W(bit4) C         E(bit0)
     *   [32][64][128]    SW(bit5) S(bit6)  SE(bit7)
     * </pre>
     * Bit 0=E(1), bit1=NE(2), bit2=N(4), bit3=NW(8),
     * bit4=W(16), bit5=SW(32), bit6=S(64), bit7=SE(128)
     */
    private static final int[][] SKEL_MASK = {{8, 4, 2}, {16, 0, 1}, {32, 64, 128}};

    /** Guo & Hall (1989) G123_LUT — exact values from skimage._skeletonize. */
    private static final boolean[] G123_LUT = makeG123Lut();
    /** Guo & Hall (1989) G123P_LUT — exact values from skimage._skeletonize. */
    private static final boolean[] G123P_LUT = makeG123PLut();

    /**
     * Binary skeletonization via Guo & Hall (1989) thinning.
     * Identical to {@code skimage.morphology.thin()} — uses the exact
     * same hard-coded G123_LUT and G123P_LUT.
     *
     * @param src binary image (0=bg, 1=fg)
     * @return 1px-wide 8-connected skeleton
     */
    public static int[][] skeletonize(int[][] src) {
        int h = src.length, w = h > 0 ? src[0].length : 0;
        if (w == 0 || h == 0) return new int[0][0];

        boolean[][] a = new boolean[h + 2][w + 2]; // padded (read)
        boolean[][] b = new boolean[h + 2][w + 2]; // cleaned (write)

        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                a[y + 1][x + 1] = src[y][x] != 0;

        boolean changed;
        do {
            changed = false;

            // Sub-iteration 1: a → b
            copy(b, a, h, w);
            for (int y = 1; y <= h; y++)
                for (int x = 1; x <= w; x++)
                    if (a[y][x] && G123_LUT[encodeNeighbors(a, x, y)]) {
                        b[y][x] = false; changed = true;
                    }
            swapRows(a, b);

            // Sub-iteration 2: a → b
            copy(b, a, h, w);
            for (int y = 1; y <= h; y++)
                for (int x = 1; x <= w; x++)
                    if (a[y][x] && G123P_LUT[encodeNeighbors(a, x, y)]) {
                        b[y][x] = false; changed = true;
                    }
            swapRows(a, b);

        } while (changed);

        int[][] out = new int[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (a[y + 1][x + 1]) out[y][x] = 1;
        return out;
    }

    private static void copy(boolean[][] dst, boolean[][] src, int h, int w) {
        for (int y = 0; y <= h + 1; y++)
            System.arraycopy(src[y], 0, dst[y], 0, w + 2);
    }

    private static void swapRows(boolean[][] x, boolean[][] y) {
        for (int r = 0; r < x.length; r++) {
            boolean[] t = x[r]; x[r] = y[r]; y[r] = t;
        }
    }

    private static int encodeNeighbors(boolean[][] img, int x, int y) {
        int idx = 0;
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++)
                if (img[y + dy][x + dx])
                    idx += SKEL_MASK[dy + 1][dx + 1];
        return idx;
    }

    // ═══════ LUTs: EXACT values from skimage._skeletonize ═══════
    // Generated by _generate_thin_luts() — Guo & Hall 1989

    private static boolean[] makeG123Lut() {
        // G1 AND G2 AND G3
        boolean[] lut = new boolean[256];
        for (int n = 0; n < 256; n++)
            lut[n] = g1(n) && g2(n) && g3(n);
        return lut;
    }

    private static boolean[] makeG123PLut() {
        // G1 AND G2 AND G3'
        boolean[] lut = new boolean[256];
        for (int n = 0; n < 256; n++)
            lut[n] = g1(n) && g2(n) && g3p(n);
        return lut;
    }

    /** G1: exactly one 0→1 transition in bit0→bit1→...→bit7→bit0 (E,NE,N,NW,W,SW,S,SE). */
    private static boolean g1(int n) {
        int s = 0;
        if (!bh(n, 0) && (bh(n, 1) || bh(n, 2))) s++;  // E→NE→N
        if (!bh(n, 2) && (bh(n, 3) || bh(n, 4))) s++;  // N→NW→W
        if (!bh(n, 4) && (bh(n, 5) || bh(n, 6))) s++;  // W→SW→S
        if (!bh(n, 6) && (bh(n, 7) || bh(n, 0))) s++;  // S→SE→E
        return s == 1;
    }

    /** G2: min(n1, n2) ∈ [2,3] — endpoint preservation. */
    private static boolean g2(int n) {
        int n1 = 0, n2 = 0;
        for (int k : new int[]{1, 3, 5, 7}) {
            if (bh(n, k) || bh(n, k - 1)) n1++;
            if (bh(n, k) || bh(n, (k + 1) % 8)) n2++;
        }
        int m = Math.min(n1, n2);
        return m == 2 || m == 3;
    }

    /** G3: SE-boundary — not ( (NE or N or not E) and S ). */
    private static boolean g3(int n) {
        return !((bh(n, 1) || bh(n, 2) || !bh(n, 7)) && bh(n, 0));
    }

    /** G3': NW-boundary — not ( (SW or S or not W) and N ). */
    private static boolean g3p(int n) {
        return !((bh(n, 5) || bh(n, 6) || !bh(n, 3)) && bh(n, 4));
    }

    private static boolean bh(int n, int i) { return (n >> i & 1) == 1; }

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

    // ═══════════════════ Corner Graph Topology ═══════════════════

    /**
     * Computes the sorted, max-normalized pairwise Euclidean distance
     * signature of corner points. Rotation/translation/scale invariant.
     *
     * <p>For N corners, computes all N(N-1)/2 pairwise distances,
     * sorts ascending, normalizes by max distance, and resamples
     * to {@code targetLen} equally-spaced values.
     *
     * <p>Two shapes with similar corner spatial arrangements will have
     * similar sorted distance distributions, regardless of contour
     * traversal order or rotation.
     *
     * @param corners   detected corner points (in original image coords)
     * @param targetLen output signature length (e.g. 15)
     * @return sorted, normalized distance ratios [0..1], length targetLen
     */
    public static double[] cornerDistanceSignature(List<Corner> corners, int targetLen) {
        int n = corners.size();
        if (n < 2) {
            // 0 or 1 corner → no pairwise distances → flat signature
            double[] flat = new double[targetLen];
            if (n == 1) java.util.Arrays.fill(flat, 0);
            return flat;
        }

        // All pairwise Euclidean distances (in original 32×32 image coords)
        int k = n * (n - 1) / 2;
        double[] dists = new double[k];
        int idx = 0;
        for (int i = 0; i < n; i++) {
            Corner ci = corners.get(i);
            for (int j = i + 1; j < n; j++) {
                Corner cj = corners.get(j);
                double dx = ci.x() - cj.x(), dy = ci.y() - cj.y();
                dists[idx++] = Math.sqrt(dx * dx + dy * dy);
            }
        }

        java.util.Arrays.sort(dists);
        double maxDist = dists[k - 1];
        if (maxDist < 1e-9) return new double[targetLen];
        for (int i = 0; i < k; i++) dists[i] /= maxDist;

        // Linear interpolation to targetLen
        return decimate(dists, targetLen);
    }

    /**
     * Computes the sorted absolute curvature (turning angle) signature
     * of corner points. Captures the distribution of corner sharpness
     * independent of rotation.
     *
     * @param corners   detected corner points
     * @param targetLen output signature length (e.g. 10)
     * @return sorted curvature magnitudes, length targetLen
     */
    public static double[] cornerAngleSignature(List<Corner> corners, int targetLen) {
        int n = corners.size();
        double[] flat = new double[targetLen];
        if (n == 0) return flat;
        if (n == 1) { java.util.Arrays.fill(flat, Math.abs(corners.get(0).curvature())); return flat; }

        double[] angles = new double[n];
        for (int i = 0; i < n; i++)
            angles[i] = Math.abs(corners.get(i).curvature());
        java.util.Arrays.sort(angles);
        return decimate(angles, targetLen);
    }

    /**
     * L2 (Euclidean) distance between two equal-length arrays.
     */
    public static double l2Norm(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            sum += d * d;
        }
        return Math.sqrt(sum) / a.length;
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

    /**
     * Finds the largest 8-connected foreground component and crops to its
     * bounding box, excluding any isolated noise pixels outside the main shape.
     *
     * @param src binary image (typically a skeleton)
     * @return new array containing only the largest 8-connected component
     */
    public static int[][] cropToForeground(int[][] src) {
        int h = src.length, w = h > 0 ? src[0].length : 0;
        if (w == 0) return new int[1][1];

        // Find largest 8-connected component and its bounding box
        boolean[][] visited = new boolean[h][w];
        int bestSize = 0;
        int bestMinX = -1, bestMinY = -1, bestMaxX = -1, bestMaxY = -1;

        for (int sy = 0; sy < h; sy++) {
            for (int sx = 0; sx < w; sx++) {
                if (src[sy][sx] == 0 || visited[sy][sx]) continue;

                java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
                q.add(new int[]{sx, sy});
                visited[sy][sx] = true;
                int size = 0;
                int cMinX = w, cMinY = h, cMaxX = -1, cMaxY = -1;

                while (!q.isEmpty()) {
                    int[] p = q.poll();
                    int cx = p[0], cy = p[1];
                    size++;
                    if (cx < cMinX) cMinX = cx; if (cx > cMaxX) cMaxX = cx;
                    if (cy < cMinY) cMinY = cy; if (cy > cMaxY) cMaxY = cy;

                    // 8-connected neighbors
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dy == 0) continue;
                            int nx = cx + dx, ny = cy + dy;
                            if (nx >= 0 && nx < w && ny >= 0 && ny < h
                                    && src[ny][nx] != 0 && !visited[ny][nx]) {
                                visited[ny][nx] = true; q.add(new int[]{nx, ny});
                            }
                        }
                }

                if (size > bestSize) {
                    bestSize = size;
                    bestMinX = cMinX; bestMinY = cMinY;
                    bestMaxX = cMaxX; bestMaxY = cMaxY;
                }
            }
        }

        if (bestSize == 0) return new int[1][1];

        int outW = bestMaxX - bestMinX + 1, outH = bestMaxY - bestMinY + 1;
        int[][] dst = new int[outH][outW];
        for (int y = bestMinY; y <= bestMaxY; y++)
            System.arraycopy(src[y], bestMinX, dst[y - bestMinY], 0, outW);
        return dst;
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

    // ═══════════════════ Skeleton Graph ═══════════════════

    /** Type of a graph node on the skeleton */
    public enum NodeType { ENDPOINT, JUNCTION, CORNER }

    /** A node in the skeleton graph */
    public record GraphNode(int id, int x, int y, int degree, NodeType type) {}

    /** An edge connecting two nodes along the skeleton, with curvature profile */
    public record GraphEdge(int fromId, int toId, int pathLength, List<int[]> path,
                            double[] curvatureProfile, double totalCurvature,
                            double curvatureStd, int inflectionCount) {}

    /** Full skeleton graph */
    public record SkeletonGraph(List<GraphNode> nodes, List<GraphEdge> edges, int totalLength) {
        public int nodeCount() { return nodes.size(); }
        public int edgeCount() { return edges.size(); }
        public List<Integer> degreeSequence() {
            return nodes.stream().map(n -> n.degree).sorted().toList();
        }
        public int endpointCount() {
            return (int) nodes.stream().filter(n -> n.type == NodeType.ENDPOINT).count();
        }
        /** Number of enclosed regions (cycles) = E - V + 1 for connected planar graph */
        public int cycleCount() { return Math.max(0, edges.size() - nodes.size() + 1); }
    }

    // ═══════════════════ Shape Context Descriptors ═══════════════════

    /** Shape Context descriptor for one skeleton graph node.
     *  histogram: linearized 5 distance bins × 12 angle bins = 60 elements.
     *  Each element is a normalized probability (sum = 1.0). */
    public record ShapeContextDescriptor(int nodeId, double[] histogram, double maxPairwiseDist) {
        public static final int DIST_BINS = 5;
        public static final int ANGLE_BINS = 12;
        public static final int HIST_SIZE = DIST_BINS * ANGLE_BINS;
    }

    /**
     * Computes Shape Context descriptors for all nodes in a skeleton graph.
     * Each descriptor is a 5×12 = 60-bin histogram of relative (distance, angle)
     * positions to all other nodes. Scale-invariant (normalized by max pairwise
     * distance) and rotation-invariant (angle bins shifted during matching).
     *
     * @param graph the skeleton graph with node (x,y) positions in 32×32 coords
     * @return one descriptor per node, in graph.nodes() list order
     */
    public static List<ShapeContextDescriptor> computeShapeContextDescriptors(SkeletonGraph graph) {
        List<GraphNode> nodes = graph.nodes();
        int n = nodes.size();
        if (n == 0) return List.of();

        // Extract positions
        int[] xs = new int[n], ys = new int[n];
        for (int i = 0; i < n; i++) { xs[i] = nodes.get(i).x(); ys[i] = nodes.get(i).y(); }

        // Compute max pairwise distance for scale normalization
        double dMax = 1e-6;
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++) {
                double d = Math.sqrt((xs[i]-xs[j])*(xs[i]-xs[j]) + (ys[i]-ys[j])*(ys[i]-ys[j]));
                if (d > dMax) dMax = d;
            }
        if (dMax < 1e-6) dMax = 1.0;

        List<ShapeContextDescriptor> descs = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double[] hist = new double[ShapeContextDescriptor.HIST_SIZE];
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                double dx = xs[j] - xs[i], dy = ys[j] - ys[i];
                double dist = Math.sqrt(dx*dx + dy*dy) / dMax;
                double angle = Math.atan2(dy, dx);
                if (angle < 0) angle += 2.0 * Math.PI;

                int dBin = Math.min(ShapeContextDescriptor.DIST_BINS - 1,
                                    (int)(ShapeContextDescriptor.DIST_BINS * dist));
                int aBin = Math.min(ShapeContextDescriptor.ANGLE_BINS - 1,
                                    (int)(ShapeContextDescriptor.ANGLE_BINS * angle / (2.0 * Math.PI)));
                hist[dBin * ShapeContextDescriptor.ANGLE_BINS + aBin] += 1.0;
            }
            // Normalize to sum=1. For single-node graphs, fill uniformly
            double sum = 0;
            for (double v : hist) sum += v;
            if (sum < 1e-9) {
                double fill = 1.0 / ShapeContextDescriptor.HIST_SIZE;
                for (int k = 0; k < hist.length; k++) hist[k] = fill;
            } else {
                for (int k = 0; k < hist.length; k++) hist[k] /= sum;
            }
            descs.add(new ShapeContextDescriptor(nodes.get(i).id(), hist, dMax));
        }
        return descs;
    }

    /**
     * Counts true enclosed regions in a skeletonized binary image using
     * flood-fill on background pixels. A true enclosed region is a
     * 4-connected component of background (0-valued) pixels that:
     * <ol>
     *   <li>does not touch the image border, and</li>
     *   <li>has area > 10 pixels (filters micro-closures from thinning/hand-drawn artifacts)</li>
     * </ol>
     *
     * <p>Uses 4-connected background fill (correct topological dual to
     * 8-connected skeleton — Rosenfeld digital topology).
     *
     * @param skeleton thinned binary image (1=foreground skeleton, 0=background)
     * @return number of true enclosed regions with area > 10
     */
    public static int detectTrueCycles(int[][] skeleton) {
        int h = skeleton.length, w = h > 0 ? skeleton[0].length : 0;
        if (w == 0) return 0;

        boolean[][] visited = new boolean[h][w];
        int trueCycles = 0;

        // 4-connected directions
        int[][] DIRS4 = {{1,0},{-1,0},{0,1},{0,-1}};

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (skeleton[y][x] != 0 || visited[y][x]) continue;

                // BFS flood-fill this background component
                ArrayDeque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{x, y});
                visited[y][x] = true;

                boolean touchesBorder = false;
                int area = 0;

                while (!queue.isEmpty()) {
                    int[] p = queue.pollFirst();
                    int cx = p[0], cy = p[1];
                    area++;

                    if (cx == 0 || cx == w - 1 || cy == 0 || cy == h - 1)
                        touchesBorder = true;

                    for (int[] d : DIRS4) {
                        int nx = cx + d[0], ny = cy + d[1];
                        if (nx >= 0 && nx < w && ny >= 0 && ny < h
                                && skeleton[ny][nx] == 0 && !visited[ny][nx]) {
                            visited[ny][nx] = true;
                            queue.add(new int[]{nx, ny});
                        }
                    }
                }

                if (!touchesBorder && area > 2)
                    trueCycles++;
            }
        }
        return trueCycles;
    }

    /**
     * Returns the area of the largest enclosed region in the skeleton.
     * Returns 0 if no enclosed region exists.
     * Useful as a noise gate: legitimate shapes have large enclosed areas.
     */
    public static int maxEnclosedArea(int[][] skeleton) {
        int h = skeleton.length, w = h > 0 ? skeleton[0].length : 0;
        if (w == 0) return 0;

        boolean[][] visited = new boolean[h][w];
        int maxArea = 0;
        int[][] DIRS4 = {{1,0},{-1,0},{0,1},{0,-1}};

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (skeleton[y][x] != 0 || visited[y][x]) continue;

                ArrayDeque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{x, y});
                visited[y][x] = true;
                boolean touchesBorder = false;
                int area = 0;

                while (!queue.isEmpty()) {
                    int[] p = queue.pollFirst();
                    area++;
                    if (p[0] == 0 || p[0] == w - 1 || p[1] == 0 || p[1] == h - 1)
                        touchesBorder = true;
                    for (int[] d : DIRS4) {
                        int nx = p[0] + d[0], ny = p[1] + d[1];
                        if (nx >= 0 && nx < w && ny >= 0 && ny < h
                                && skeleton[ny][nx] == 0 && !visited[ny][nx]) {
                            visited[ny][nx] = true;
                            queue.add(new int[]{nx, ny});
                        }
                    }
                }

                if (!touchesBorder && area > maxArea)
                    maxArea = area;
            }
        }
        return maxArea;
    }

    /**
     * Counts true cycles from a skeleton graph by filtering out
     * micro-edges (length < 8 pixels) and recomputing Euler's formula.
     * Micro-edges from thinning artifacts create spurious cycles that
     * the raster flood-fill can't distinguish from genuine topology.
     */
    public static int graphTrueCycles(SkeletonGraph graph) {
        var edges = graph.edges();
        var nodes = graph.nodes();
        int n = nodes.size();
        if (n == 0) return 0;

        // Count edges that are NOT micro-artifacts
        int realEdges = 0;
        for (var e : edges) {
            if (e.pathLength() >= 8) realEdges++;
        }

        // Count nodes that participate in real edges
        java.util.Set<Integer> activeNodeIds = new java.util.HashSet<>();
        for (var e : edges) {
            if (e.pathLength() >= 8) {
                activeNodeIds.add(e.fromId());
                activeNodeIds.add(e.toId());
            }
        }
        int realNodes = activeNodeIds.size();

        return Math.max(0, realEdges - realNodes + 1);
    }

    /** Number of 8-connected skeleton neighbors at (x,y) */
    private static int countNeighbors(int[][] grid, int x, int y) {
        int h = grid.length, w = h > 0 ? grid[0].length : 0;
        int c = 0;
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++)
                if (dx != 0 || dy != 0) {
                    int ny = y + dy, nx = x + dx;
                    if (ny >= 0 && ny < h && nx >= 0 && nx < w && grid[ny][nx] != 0) c++;
                }
        return c;
    }

    /**
     * Prunes short branches from a skeleton by iteratively removing
     * endpoint branches shorter than {@code minBranchRatio} of total length.
     *
     * @return pruned skeleton (new array)
     */
    public static int[][] pruneSkeleton(int[][] skeleton, double minBranchRatio) {
        int h = skeleton.length, w = h > 0 ? skeleton[0].length : 0;
        boolean[][] s = new boolean[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                s[y][x] = skeleton[y][x] != 0;

        int totalLen = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (s[y][x]) totalLen++;
        if (totalLen < 3) {
            int[][] out = new int[h][w];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    if (s[y][x]) out[y][x] = 1;
            return out;
        }
        int minLen = Math.max(2, (int)(totalLen * minBranchRatio));

        // Iterative pruning: trace from each endpoint, delete if branch too short
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int y = 0; y < h && !changed; y++) {
                for (int x = 0; x < w && !changed; x++) {
                    if (!s[y][x] || countSkelNeighbors(s, x, y) != 1) continue;

                    // Trace from this endpoint to the nearest junction
                    List<int[]> branch = new ArrayList<>();
                    branch.add(new int[]{x, y});
                    int cx = x, cy = y, px = -1, py = -1;
                    while (true) {
                        int oldCx = cx, oldCy = cy;
                        int[] next = findSkelNext(s, cx, cy, px, py);
                        if (next == null) break;
                        branch.add(next);
                        cx = next[0]; cy = next[1];
                        px = oldCx; py = oldCy;
                        int ndeg = countSkelNeighbors(s, cx, cy);
                        if (ndeg >= 3 || ndeg == 1) break;
                    }

                    if (branch.size() < minLen) {
                        for (int[] p : branch) s[p[1]][p[0]] = false;
                        changed = true;
                    }
                }
            }
        }

        int[][] out = new int[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (s[y][x]) out[y][x] = 1;
        return out;
    }

    /** Find the one skeleton neighbor of (x,y) that isn't (prevX,prevY) */
    private static int[] findSkelNext(boolean[][] s, int x, int y, int prevX, int prevY) {
        int h = s.length, w = h > 0 ? s[0].length : 0;
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx, ny = y + dy;
                if (ny >= 0 && ny < h && nx >= 0 && nx < w && s[ny][nx]
                        && !(nx == prevX && ny == prevY))
                    return new int[]{nx, ny};
            }
        return null;
    }

    private static int countSkelNeighbors(boolean[][] s, int x, int y) {
        int h = s.length, w = h > 0 ? s[0].length : 0;
        int c = 0;
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++)
                if (dx != 0 || dy != 0) {
                    int ny = y + dy, nx = x + dx;
                    if (ny >= 0 && ny < h && nx >= 0 && nx < w && s[ny][nx]) c++;
                }
        return c;
    }

    // ═══════════════════ Edge Curvature ═══════════════════

    /** Default number of resampled curvature samples per edge */
    private static final int EDGE_CURV_SAMPLES = 16;
    /** |curvature| threshold for detecting a corner on an edge (radians) */
    private static final double CORNER_CURV_THRESHOLD = 0.35;

    /**
     * Computes curvature profile along a skeleton path.
     * Returns {profile[M], totalCurvature, curvatureStd, inflectionCount}.
     */
    private static double[][] computeEdgeCurvature(List<int[]> path, int M) {
        int n = path.size();
        double[] profile = new double[M];
        double totalCurv = 0, curvStd = 0;
        int inflections = 0;

        if (n < 3) return new double[][]{profile, {totalCurv}, {curvStd}, {(double)inflections}};

        // Compute turning angles at interior points
        double[] turns = new double[n - 2];
        for (int i = 1; i < n - 1; i++) {
            int[] p0 = path.get(i - 1), p1 = path.get(i), p2 = path.get(i + 1);
            double inAngle = Math.atan2(p1[1] - p0[1], p1[0] - p0[0]);
            double outAngle = Math.atan2(p2[1] - p1[1], p2[0] - p1[0]);
            double t = outAngle - inAngle;
            while (t > Math.PI) t -= 2 * Math.PI;
            while (t < -Math.PI) t += 2 * Math.PI;
            turns[i - 1] = t;
        }
        int turnsLen = turns.length;

        // Accumulate stats + inflection count
        double prevSign = Math.signum(turns[0]);
        for (double t : turns) {
            double absT = Math.abs(t);
            totalCurv += absT;
            double s = Math.signum(t);
            if (s != 0 && s != prevSign) { inflections++; prevSign = s; }
        }
        double mean = totalCurv / turnsLen;
        double var = 0;
        for (double t : turns) var += (Math.abs(t) - mean) * (Math.abs(t) - mean);
        curvStd = Math.sqrt(var / turnsLen);

        // Resample to M samples via linear interpolation
        profile = decimate(turns, M);
        return new double[][]{profile, {totalCurv}, {curvStd}, {(double)inflections}};
    }

    /**
     * Inserts CORNER nodes into the graph by splitting edges at high-curvature
     * points (inflections and curvature peaks). Returns a new SkeletonGraph
     * with corner nodes inserted and edges carrying curvature profiles.
     */
    public static SkeletonGraph insertCornerNodes(SkeletonGraph graph, int[][] skeleton) {
        List<GraphNode> nodes = new ArrayList<>(graph.nodes());
        List<GraphEdge> newEdges = new ArrayList<>();
        int nextNodeId = nodes.size();

        for (GraphEdge edge : graph.edges()) {
            List<int[]> path = edge.path();
            int n = path.size();
            if (n < 3) {
                newEdges.add(makeCurvedEdge(edge.fromId(), edge.toId(), path));
                continue;
            }

            // Compute curvature at each interior point
            double[] turns = new double[n - 2];
            int[] turnIndices = new int[n - 2]; // original path index (1-based)
            for (int i = 1; i < n - 1; i++) {
                int[] p0 = path.get(i - 1), p1 = path.get(i), p2 = path.get(i + 1);
                double inA = Math.atan2(p1[1] - p0[1], p1[0] - p0[0]);
                double outA = Math.atan2(p2[1] - p1[1], p2[0] - p1[0]);
                double t = outA - inA;
                while (t > Math.PI) t -= 2 * Math.PI;
                while (t < -Math.PI) t += 2 * Math.PI;
                turns[i - 1] = t;
                turnIndices[i - 1] = i;
            }

            // Find curvature peaks above threshold
            boolean[] isPeak = new boolean[turns.length];
            for (int i = 0; i < turns.length; i++) {
                double absT = Math.abs(turns[i]);
                if (absT < CORNER_CURV_THRESHOLD) continue;
                double left = (i > 0) ? Math.abs(turns[i - 1]) : 0;
                double right = (i < turns.length - 1) ? Math.abs(turns[i + 1]) : 0;
                if (absT >= left && absT >= right) isPeak[i] = true;
            }
            // Suppress close peaks (< 3 indices apart)
            for (int i = 0; i < turns.length; i++) {
                if (!isPeak[i]) continue;
                for (int d = 1; d <= 3; d++) {
                    int ni = i + d;
                    if (ni < turns.length && isPeak[ni] && Math.abs(turns[ni]) > Math.abs(turns[i])) {
                        isPeak[i] = false; break;
                    }
                    ni = i - d;
                    if (ni >= 0 && isPeak[ni] && Math.abs(turns[ni]) > Math.abs(turns[i])) {
                        isPeak[i] = false; break;
                    }
                }
            }

            // Split edge at peaks, creating sub-edges with curvature profiles
            int prevNodeId = edge.fromId();
            int lastSplitIdx = 0;
            for (int i = 0; i < turns.length; i++) {
                if (!isPeak[i]) continue;
                int pathIdx = turnIndices[i]; // 1-based index in original path
                int[] cornerPt = path.get(pathIdx);

                // Create corner node
                int cornerId = nextNodeId++;
                int cornerX = cornerPt[0], cornerY = cornerPt[1];
                nodes.add(new GraphNode(cornerId, cornerX, cornerY, 2, NodeType.CORNER));

                // Sub-path from prevNodeId/point to this corner
                List<int[]> subPath = path.subList(lastSplitIdx, pathIdx + 1);
                newEdges.add(makeCurvedEdge(prevNodeId, cornerId, subPath));

                prevNodeId = cornerId;
                lastSplitIdx = pathIdx;
            }
            // Final sub-path from last split to end node
            List<int[]> finalPath = path.subList(lastSplitIdx, n);
            newEdges.add(makeCurvedEdge(prevNodeId, edge.toId(), finalPath));
        }

        // Recompute total length
        int totalLen = 0;
        for (GraphEdge e : newEdges) totalLen += e.pathLength();
        return new SkeletonGraph(nodes, newEdges, totalLen);
    }

    /** Creates a GraphEdge with computed curvature profile */
    private static GraphEdge makeCurvedEdge(int fromId, int toId, List<int[]> path) {
        double[][] curvData = computeEdgeCurvature(path, EDGE_CURV_SAMPLES);
        return new GraphEdge(fromId, toId, path.size(), path,
                curvData[0], curvData[1][0], curvData[2][0], (int)curvData[3][0]);
    }

    /**
     * Builds a skeleton graph from a thinned binary image.
     *
     * <p>Nodes: endpoints (degree=1) and junctions (degree ≥ 3).
     * Edges: skeleton paths connecting nodes.
     *
     * <p>Returns null for simple loops with no nodes (e.g., perfect circles).
     */
    public static SkeletonGraph buildSkeletonGraph(int[][] skeleton) {
        int h = skeleton.length, w = h > 0 ? skeleton[0].length : 0;
        if (w == 0) return new SkeletonGraph(List.of(), List.of(), 0);

        // Phase 1: find all nodes and assign IDs
        List<GraphNode> nodes = new ArrayList<>();
        int[][] nodeId = new int[h][w]; // 0 = no node, >0 = node index (1-based)
        for (int y = 0; y < h; y++) Arrays.fill(nodeId[y], -1);

        int totalLen = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (skeleton[y][x] != 0) {
                    totalLen++;
                    int deg = countNeighbors(skeleton, x, y);
                    if (deg == 1 || deg >= 3) {
                        int id = nodes.size();
                        NodeType type = deg == 1 ? NodeType.ENDPOINT : NodeType.JUNCTION;
                        nodes.add(new GraphNode(id, x, y, deg, type));
                        nodeId[y][x] = id;
                    }
                }

        if (totalLen < 2) return new SkeletonGraph(nodes, List.of(), totalLen);
        if (nodes.isEmpty()) {
            // Pure loop — find any skeleton pixel as virtual node
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    if (skeleton[y][x] != 0) {
                        nodes.add(new GraphNode(0, x, y, 2, NodeType.ENDPOINT));
                        nodeId[y][x] = 0;
                        y = h; break;
                    }
            // Also add a second virtual node at the opposite side of loop for edge
            if (nodes.size() == 1) {
                GraphNode n0 = nodes.get(0);
                int fx = -1, fy = -1, maxD = 0;
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++)
                        if (skeleton[y][x] != 0 && nodeId[y][x] < 0) {
                            int d = (x-n0.x)*(x-n0.x) + (y-n0.y)*(y-n0.y);
                            if (d > maxD) { maxD = d; fx = x; fy = y; }
                        }
                if (fx >= 0) {
                    nodes.add(new GraphNode(1, fx, fy, 2, NodeType.ENDPOINT));
                    nodeId[fy][fx] = 1;
                }
            }
        }

        // Phase 2: trace edges between nodes
        List<GraphEdge> edges = new ArrayList<>();
        boolean[][] visitedEdge = new boolean[h][w];

        for (GraphNode node : nodes) {
            int x = node.x(), y = node.y();
            // Try all neighbors of this node
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int nx = x + dx, ny = y + dy;
                    if (ny < 0 || ny >= h || nx < 0 || nx >= w) continue;
                    if (skeleton[ny][nx] == 0) continue;
                    if (visitedEdge[ny][nx]) continue;

                    // Trace from this neighbor until we hit another node
                    int prevX = x, prevY = y;
                    int cx = nx, cy = ny;
                    List<int[]> path = new ArrayList<>();
                    boolean reachedNode = false;
                    int steps = 0;

                    while (steps < w * h) {
                        path.add(new int[]{cx, cy});
                        visitedEdge[cy][cx] = true;
                        steps++;

                        int nid = nodeId[cy][cx];
                        if (nid >= 0 && nid != node.id()) {
                            // Reached another node — create edge
                            edges.add(makeCurvedEdge(node.id(), nid, path));
                            reachedNode = true;
                            break;
                        }

                        // Move to next unvisited skeleton neighbor
                        boolean moved = false;
                        int bestDeg = 999, bestNx = -1, bestNy = -1;
                        for (int ddy = -1; ddy <= 1; ddy++) {
                            for (int ddx = -1; ddx <= 1; ddx++) {
                                if (ddx == 0 && ddy == 0) continue;
                                int tnx = cx + ddx, tny = cy + ddy;
                                if (tny < 0 || tny >= h || tnx < 0 || tnx >= w) continue;
                                if (skeleton[tny][tnx] == 0) continue;
                                if (tnx == prevX && tny == prevY) continue;
                                if (visitedEdge[tny][tnx]) continue;
                                // Prefer the neighbor that goes toward an unvisited node
                                int ndeg = countNeighbors(skeleton, tnx, tny);
                                int tnid = nodeId[tny][tnx];
                                // Prioritize: other node first, then lower degree (stay on path)
                                if (tnid >= 0 && tnid != node.id()) {
                                    bestNx = tnx; bestNy = tny; bestDeg = -1;
                                } else if (bestDeg > 0 && ndeg <= 2) {
                                    bestNx = tnx; bestNy = tny; bestDeg = ndeg;
                                }
                            }
                        }
                        if (bestNx >= 0) {
                            prevX = cx; prevY = cy;
                            cx = bestNx; cy = bestNy;
                            moved = true;
                        }
                        if (!moved) break;
                    }

                    // If we didn't reach another node but traced a loop back, create edge
                    if (!reachedNode && steps > 1) {
                        int nid = nodeId[cy][cx];
                        if (nid >= 0) {
                            edges.add(makeCurvedEdge(node.id(), nid, path));
                        }
                    }
                }
            }
        }

        // Remove duplicate edges (same nodes in opposite order)
        List<GraphEdge> deduped = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (GraphEdge e : edges) {
            String key = e.fromId() <= e.toId()
                ? e.fromId() + "_" + e.toId()
                : e.toId() + "_" + e.fromId();
            if (seen.add(key)) deduped.add(e);
        }

        // Phase 3: insert corner nodes and compute edge curvature profiles
        SkeletonGraph basic = new SkeletonGraph(nodes, deduped, totalLen);
        return insertCornerNodes(basic, skeleton);
    }

}
