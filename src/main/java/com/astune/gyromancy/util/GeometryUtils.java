package com.astune.gyromancy.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Static geometric computation utilities for the symbol recognition engine.
 * All methods are pure math with no Minecraft dependencies.
 *
 * Covers:
 * <ul>
 *   <li>Hu invariant moments (7 rotation/scale/mirror invariants)</li>
 *   <li>PCA-based orientation detection</li>
 *   <li>Sobel edge direction histogram (8 bins)</li>
 *   <li>Image normalization (centering + rescaling)</li>
 *   <li>Distance metrics for matching</li>
 * </ul>
 */
public final class GeometryUtils {

    private GeometryUtils() {}

    // ═══════════════════════════════════════════════════════════════
    // Hu Invariant Moments
    // ═══════════════════════════════════════════════════════════════

    /**
     * Computes the 7 Hu invariant moments for a binary image.
     * These are invariant to translation, scale, rotation, and mirroring.
     *
     * @param binaryImage 2D array, non-zero = foreground. Row-major: [y][x].
     * @return array of 7 Hu moment values (log|Hu_i| for numerical stability)
     */
    public static double[] computeHuMoments(int[][] binaryImage) {
        int h = binaryImage.length;
        int w = h > 0 ? binaryImage[0].length : 0;
        if (w == 0) return new double[7];

        // Compute centroid
        double m00 = 0, cx = 0, cy = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int val = binaryImage[y][x];
                if (val != 0) {
                    m00 += val;
                    cx += x * val;
                    cy += y * val;
                }
            }
        }
        if (m00 == 0) return new double[7];
        cx /= m00;
        cy /= m00;

        // Central moments up to order 3
        double mu11 = 0, mu20 = 0, mu02 = 0, mu21 = 0, mu12 = 0, mu30 = 0, mu03 = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int val = binaryImage[y][x];
                if (val == 0) continue;
                double dx = x - cx;
                double dy = y - cy;
                mu11 += dx * dy * val;
                mu20 += dx * dx * val;
                mu02 += dy * dy * val;
                mu21 += dx * dx * dy * val;
                mu12 += dx * dy * dy * val;
                mu30 += dx * dx * dx * val;
                mu03 += dy * dy * dy * val;
            }
        }

        // Normalized central moments: eta_pq = mu_pq / mu_00^((p+q)/2 + 1)
        double m00pow2 = m00 * m00;     // mu_00^2
        double m00pow25 = m00 * m00 * Math.sqrt(m00); // mu_00^2.5
        double m00pow3 = m00pow2 * m00;  // mu_00^3

        double eta11 = mu11 / m00pow2;
        double eta20 = mu20 / m00pow2;
        double eta02 = mu02 / m00pow2;
        double eta21 = mu21 / m00pow25;
        double eta12 = mu12 / m00pow25;
        double eta30 = mu30 / m00pow25;
        double eta03 = mu03 / m00pow25;

        // 7 Hu moments
        double hu1 = eta20 + eta02;
        double hu2 = (eta20 - eta02) * (eta20 - eta02) + 4 * eta11 * eta11;
        double hu3 = (eta30 - 3 * eta12) * (eta30 - 3 * eta12) + (3 * eta21 - eta03) * (3 * eta21 - eta03);
        double hu4 = (eta30 + eta12) * (eta30 + eta12) + (eta21 + eta03) * (eta21 + eta03);
        double hu5 = (eta30 - 3 * eta12) * (eta30 + eta12)
                * ((eta30 + eta12) * (eta30 + eta12) - 3 * (eta21 + eta03) * (eta21 + eta03))
                + (3 * eta21 - eta03) * (eta21 + eta03)
                * (3 * (eta30 + eta12) * (eta30 + eta12) - (eta21 + eta03) * (eta21 + eta03));
        double hu6 = (eta20 - eta02) * ((eta30 + eta12) * (eta30 + eta12) - (eta21 + eta03) * (eta21 + eta03))
                + 4 * eta11 * (eta30 + eta12) * (eta21 + eta03);
        double hu7 = (3 * eta21 - eta03) * (eta30 + eta12)
                * ((eta30 + eta12) * (eta30 + eta12) - 3 * (eta21 + eta03) * (eta21 + eta03))
                - (eta30 - 3 * eta12) * (eta21 + eta03)
                * (3 * (eta30 + eta12) * (eta30 + eta12) - (eta21 + eta03) * (eta21 + eta03));

        // Log-transform for numerical stability
        return new double[] {
                signLog(hu1), signLog(hu2), signLog(hu3),
                signLog(hu4), signLog(hu5), signLog(hu6), signLog(hu7)
        };
    }

    private static double signLog(double value) {
        if (Math.abs(value) < 1e-15) return 0;
        return Math.signum(value) * Math.log10(Math.abs(value));
    }

    // ═══════════════════════════════════════════════════════════════
    // PCA Orientation Detection
    // ═══════════════════════════════════════════════════════════════

    /**
     * Result of PCA analysis for orientation detection.
     */
    public record PCAResult(
            /** Primary axis orientation in degrees (0–180) */
            float angleDegrees,
            /** Variance along the primary axis */
            double eigenvalue1,
            /** Variance along the secondary axis */
            double eigenvalue2
    ) {}

    /**
     * Computes PCA on the foreground pixel distribution to estimate
     * the primary orientation axis of the drawn symbol.
     *
     * @param binaryImage 2D binary image, [y][x], non-zero = foreground
     * @return PCA result with primary axis angle and eigenvalues
     */
    public static PCAResult computePCA(int[][] binaryImage) {
        int h = binaryImage.length;
        int w = h > 0 ? binaryImage[0].length : 0;

        // Collect foreground pixel coordinates
        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (binaryImage[y][x] != 0) {
                    xs.add((double) x);
                    ys.add((double) y);
                }
            }
        }

        int n = xs.size();
        if (n < 2) return new PCAResult(0f, 0, 0);

        // Compute mean
        double meanX = 0, meanY = 0;
        for (int i = 0; i < n; i++) {
            meanX += xs.get(i);
            meanY += ys.get(i);
        }
        meanX /= n;
        meanY /= n;

        // Compute covariance matrix
        double covXX = 0, covYY = 0, covXY = 0;
        for (int i = 0; i < n; i++) {
            double dx = xs.get(i) - meanX;
            double dy = ys.get(i) - meanY;
            covXX += dx * dx;
            covYY += dy * dy;
            covXY += dx * dy;
        }
        covXX /= n;
        covYY /= n;
        covXY /= n;

        // Eigen decomposition of 2x2 covariance matrix
        double trace = covXX + covYY;
        double det = covXX * covYY - covXY * covXY;
        double disc = Math.sqrt(Math.max(0, trace * trace / 4 - det));

        double lambda1 = trace / 2 + disc;  // larger eigenvalue
        double lambda2 = trace / 2 - disc;  // smaller eigenvalue

        // Angle of primary eigenvector: 0.5 * atan2(2*covXY, covXX - covYY)
        double angleRad = 0.5 * Math.atan2(2 * covXY, covXX - covYY);
        float angleDeg = (float) Math.toDegrees(angleRad);
        // Normalize to 0–180
        if (angleDeg < 0) angleDeg += 180f;

        return new PCAResult(angleDeg, lambda1, lambda2);
    }

    // ═══════════════════════════════════════════════════════════════
    // Sobel Edge Direction Histogram
    // ═══════════════════════════════════════════════════════════════

    /**
     * Computes an 8-direction edge histogram using Sobel gradient operators.
     *
     * @param binaryImage 2D binary image, [y][x]
     * @return array of 8 direction bin values (sum of gradient magnitudes):
     *         [0°, 45°, 90°, 135°, 180°, 225°, 270°, 315°]
     */
    public static int[] computeEdgeHistogram(int[][] binaryImage) {
        int h = binaryImage.length;
        int w = h > 0 ? binaryImage[0].length : 0;
        int[] histogram = new int[8];

        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                if (binaryImage[y][x] == 0) continue;

                // Sobel operators
                int gx = (-1 * getBinary(binaryImage, x - 1, y - 1))
                        + (0 * getBinary(binaryImage, x, y - 1))
                        + (1 * getBinary(binaryImage, x + 1, y - 1))
                        + (-2 * getBinary(binaryImage, x - 1, y))
                        + (0 * getBinary(binaryImage, x, y))
                        + (2 * getBinary(binaryImage, x + 1, y))
                        + (-1 * getBinary(binaryImage, x - 1, y + 1))
                        + (0 * getBinary(binaryImage, x, y + 1))
                        + (1 * getBinary(binaryImage, x + 1, y + 1));

                int gy = (-1 * getBinary(binaryImage, x - 1, y - 1))
                        + (-2 * getBinary(binaryImage, x, y - 1))
                        + (-1 * getBinary(binaryImage, x + 1, y - 1))
                        + (0 * getBinary(binaryImage, x - 1, y))
                        + (0 * getBinary(binaryImage, x, y))
                        + (0 * getBinary(binaryImage, x + 1, y))
                        + (1 * getBinary(binaryImage, x - 1, y + 1))
                        + (2 * getBinary(binaryImage, x, y + 1))
                        + (1 * getBinary(binaryImage, x + 1, y + 1));

                if (gx == 0 && gy == 0) continue;

                // Gradient direction → angle in degrees (0–360)
                double angle = Math.toDegrees(Math.atan2(gy, gx));
                if (angle < 0) angle += 360;

                // Quantize to 8 bins: bin 0 = [0,45), bin 1 = [45,90), ...
                int bin = ((int) (angle / 45.0)) % 8;

                // Weight by gradient magnitude
                int magnitude = (int) Math.sqrt(gx * gx + gy * gy);
                histogram[bin] += magnitude;
            }
        }

        return histogram;
    }

    /** Helper: returns 1 for non-zero, 0 for zero, safe for OOB */
    private static int getBinary(int[][] image, int x, int y) {
        if (y < 0 || y >= image.length || x < 0 || x >= image[0].length) return 0;
        return image[y][x] != 0 ? 1 : 0;
    }

    // ═══════════════════════════════════════════════════════════════
    // Image Normalization
    // ═══════════════════════════════════════════════════════════════

    /**
     * Normalizes a binary image to a fixed target size.
     * Steps: compute centroid → center → scale to target dimensions via area sampling.
     *
     * @param source  source binary image (any size), [y][x], non-zero = foreground
     * @param targetW target width (typically 32)
     * @param targetH target height (typically 32)
     * @return normalized binary image (values 0 or 1), [y][x]
     */
    public static int[][] normalize(int[][] source, int targetW, int targetH) {
        int sh = source.length;
        int sw = sh > 0 ? source[0].length : 0;
        if (sw == 0 || sh == 0) {
            return new int[targetH][targetW];
        }

        // Compute centroid and bounding box
        double[] centroid = computeCentroid(source);
        int[] bbox = computeBoundingBox(source);
        int bboxW = bbox[2] - bbox[0] + 1;
        int bboxH = bbox[3] - bbox[1] + 1;

        if (bboxW <= 0 || bboxH <= 0) {
            return new int[targetH][targetW];
        }

        // Compute the region to extract: centered on centroid, padded by 10%
        double cx = centroid[0];
        double cy = centroid[1];
        double padFactor = 0.1;
        double halfW = Math.max(bboxW / 2.0 * (1 + padFactor), 1);
        double halfH = Math.max(bboxH / 2.0 * (1 + padFactor), 1);

        // Ensure square aspect ratio
        double halfSize = Math.max(halfW, halfH);
        halfW = halfSize;
        halfH = halfSize;

        double srcMinX = cx - halfW;
        double srcMinY = cy - halfH;

        int[][] result = new int[targetH][targetW];

        for (int ty = 0; ty < targetH; ty++) {
            for (int tx = 0; tx < targetW; tx++) {
                // Map target pixel to source pixel (area sampling)
                double sx = srcMinX + (tx + 0.5) * (2 * halfW) / targetW;
                double sy = srcMinY + (ty + 0.5) * (2 * halfH) / targetH;

                int isx = (int) Math.floor(sx);
                int isy = (int) Math.floor(sy);

                // Bilinear-ish: check 4 nearest pixels
                int count = 0;
                if (isy >= 0 && isy < sh && isx >= 0 && isx < sw && source[isy][isx] != 0) count++;
                if (isy >= 0 && isy < sh && isx + 1 >= 0 && isx + 1 < sw && source[isy][isx + 1] != 0) count++;
                if (isy + 1 >= 0 && isy + 1 < sh && isx >= 0 && isx < sw && source[isy + 1][isx] != 0) count++;
                if (isy + 1 >= 0 && isy + 1 < sh && isx + 1 >= 0 && isx + 1 < sw && source[isy + 1][isx + 1] != 0) count++;

                result[ty][tx] = count >= 2 ? 1 : 0;
            }
        }

        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // Geometric Properties
    // ═══════════════════════════════════════════════════════════════

    /**
     * Computes the bounding box of all non-zero pixels.
     * @return [minX, minY, maxX, maxY]; returns zeros if image is empty
     */
    public static int[] computeBoundingBox(int[][] binaryImage) {
        int h = binaryImage.length;
        int w = h > 0 ? binaryImage[0].length : 0;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (binaryImage[y][x] != 0) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }

        if (minX == Integer.MAX_VALUE) {
            return new int[]{0, 0, 0, 0};
        }
        return new int[]{minX, minY, maxX, maxY};
    }

    /**
     * Computes the intensity-weighted center of mass.
     * @return [centerX, centerY]
     */
    public static double[] computeCentroid(int[][] binaryImage) {
        int h = binaryImage.length;
        int w = h > 0 ? binaryImage[0].length : 0;
        double totalMass = 0, cx = 0, cy = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int val = binaryImage[y][x];
                if (val != 0) {
                    totalMass += val;
                    cx += x * val;
                    cy += y * val;
                }
            }
        }

        if (totalMass == 0) return new double[]{0, 0};
        return new double[]{cx / totalMass, cy / totalMass};
    }

    /**
     * Counts the number of non-zero pixels.
     */
    public static int computeArea(int[][] binaryImage) {
        int area = 0;
        for (int[] row : binaryImage) {
            for (int val : row) {
                if (val != 0) area++;
            }
        }
        return area;
    }

    /**
     * Computes the aspect ratio (width/height) of the foreground bounding box.
     */
    public static double computeAspectRatio(int[][] binaryImage) {
        int[] bbox = computeBoundingBox(binaryImage);
        int bw = bbox[2] - bbox[0] + 1;
        int bh = bbox[3] - bbox[1] + 1;
        if (bh == 0) return 0;
        return (double) bw / bh;
    }

    // ═══════════════════════════════════════════════════════════════
    // Distance Metrics
    // ═══════════════════════════════════════════════════════════════

    /**
     * Weight for each Hu moment (higher moments tend to be noisier).
     */
    private static final double[] HU_WEIGHTS = {1.0, 0.8, 0.6, 0.5, 0.3, 0.2, 0.1};

    /**
     * Computes the weighted Euclidean distance between two Hu moment vectors.
     * @return distance value; lower = more similar
     */
    public static double huDistance(double[] hu1, double[] hu2) {
        double dist = 0;
        for (int i = 0; i < 7; i++) {
            double diff = (hu1[i] - hu2[i]) * HU_WEIGHTS[i];
            dist += diff * diff;
        }
        return Math.sqrt(dist);
    }

    /**
     * Normalizes a Hu distance to a 0–1 similarity score.
     * @param distance raw huDistance value
     * @return score 0.0 (completely different) to 1.0 (identical)
     */
    public static double huScore(double distance) {
        return Math.exp(-distance * 0.5);
    }

    /**
     * Computes histogram intersection distance between two edge histograms.
     * @return distance 0.0 (identical) to 1.0 (completely different)
     */
    public static double edgeHistogramDistance(int[] hist1, int[] hist2) {
        if (hist1.length != hist2.length) return 1.0;

        int sum1 = Arrays.stream(hist1).sum();
        int sum2 = Arrays.stream(hist2).sum();

        if (sum1 == 0 && sum2 == 0) return 0.0;
        if (sum1 == 0 || sum2 == 0) return 1.0;

        double intersection = 0;
        for (int i = 0; i < hist1.length; i++) {
            double norm1 = (double) hist1[i] / sum1;
            double norm2 = (double) hist2[i] / sum2;
            intersection += Math.min(norm1, norm2);
        }

        return 1.0 - intersection;
    }

    /**
     * Converts edge histogram distance to a 0–1 similarity score.
     */
    public static double edgeScore(double distance) {
        return 1.0 - Math.min(distance, 1.0);
    }

    /**
     * Computes pixel overlap ratio between two normalized images of the same size.
     * @return score 0.0 (no overlap) to 1.0 (perfect overlap)
     */
    public static double overlapScore(int[][] img1, int[][] img2) {
        int h1 = img1.length;
        int h2 = img2.length;
        if (h1 == 0 || h2 == 0) return 0.0;
        if (h1 != h2 || img1[0].length != img2[0].length) return 0.0;

        int w = img1[0].length;
        int intersection = 0;
        int union = 0;

        for (int y = 0; y < h1; y++) {
            for (int x = 0; x < w; x++) {
                boolean a = img1[y][x] != 0;
                boolean b = img2[y][x] != 0;
                if (a && b) intersection++;
                if (a || b) union++;
            }
        }

        return union == 0 ? 0.0 : (double) intersection / union;
    }
}
