package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.symbol.SymbolTemplate;
import com.astune.gyromancy.util.GeometryUtils;
import com.astune.gyromancy.util.GeometryUtils.Corner;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Multi-descriptor elastic shape matching for hand-drawn symbol recognition.
 *
 * <p>Ensemble of six complementary descriptors, all rotation/scale invariant:
 * <ol>
 *   <li>Curvature κ(s) — DTW elastic matching (primary structural)</li>
 *   <li>CDF r(s) — L2 cyclic shift (radial profile)</li>
 *   <li>TF Θ(s) — L2 cyclic shift (global shape, noise rejection)</li>
 *   <li>CSS corners — topological cross-check</li>
 *   <li>CDF skewness — asymmetry discriminator (water vs fire)</li>
 *   <li>Pixel count ratio — area similarity</li>
 * </ol>
 *
 * <p>Key innovation: matching curvature (non-cumulative) instead of
 * turning function (cumulative) eliminates the global error propagation
 * that caused hand-drawn variants to fail with the original approach.
 */
public final class GeometricMatcher {

    private static final double LAMBDA_CURV = 8.0;   // DTW curvature
    private static final double LAMBDA_CDF = 5.0;
    private static final double LAMBDA_TF = 35.0;
    private static final double CURV_SIGMA = 1.5;
    private static final int DTW_BAND = 8;
    private static final int TF_SAMPLES = 72;
    private static final int CORNER_WINDOW = 3;

    private GeometricMatcher() {}

    public record MatchResult(
            float confidence,
            float rotationDegrees,
            boolean mirrored,
            float scale,
            float tfScore
    ) {
        public static final MatchResult NONE = new MatchResult(0f, 0f, false, 1f, 0f);
    }

    public static MatchResult match(int[][] drawn, SymbolTemplate template) {
        // 1. Trace contour + compute TF + CDF (thick) + detect corners (thinned)
        GeometryUtils.Contour contour = GeometryUtils.traceContour(drawn);
        double[] tfDrawn = GeometryUtils.turningFunction(contour, TF_SAMPLES);
        double[] cdfDrawn = GeometryUtils.centroidDistanceFunction(contour, TF_SAMPLES);
        GeometryUtils.Contour thinned = GeometryUtils.traceThinnedContour(drawn);
        List<Corner> drawnCorners = GeometryUtils.detectCorners(thinned, 4);

        // 2. Template precomputed descriptors
        double[] tfTpl = template.turningFunction();
        double[] cdfTpl = template.centroidDistanceFunction();
        List<Corner> tplCorners = template.corners();

        // 3. Curvature — smoothed + DTW elastic
        double[] curvDrawn = GeometryUtils.curvatureFromTurningFunction(tfDrawn, CURV_SIGMA);
        double[] curvTpl = GeometryUtils.curvatureFromTurningFunction(tfTpl, CURV_SIGMA);

        double curvDist = GeometryUtils.cyclicDtwDistance(curvDrawn, curvTpl, DTW_BAND);
        if (template.allowMirror()) {
            double[] curvDrawnMir = new double[TF_SAMPLES];
            for (int i = 0; i < TF_SAMPLES; i++)
                curvDrawnMir[i] = -curvDrawn[TF_SAMPLES - 1 - i];
            double mirrorDist = GeometryUtils.cyclicDtwDistance(curvDrawnMir, curvTpl, DTW_BAND);
            curvDist = Math.min(curvDist, mirrorDist);
        }
        float curvScore = (float) Math.exp(-LAMBDA_CURV * curvDist);

        // 4. CDF distance with L2 cyclic shift
        double cdfDist = GeometryUtils.tfDistance(cdfDrawn, cdfTpl, false);
        if (template.allowMirror()) {
            double[] cdfDrawnMir = new double[TF_SAMPLES];
            for (int i = 0; i < TF_SAMPLES; i++)
                cdfDrawnMir[i] = cdfDrawn[TF_SAMPLES - 1 - i];
            double mirrorDist = GeometryUtils.tfDistance(cdfDrawnMir, cdfTpl, false);
            cdfDist = Math.min(cdfDist, mirrorDist);
        }
        float cdfScore = (float) Math.exp(-LAMBDA_CDF * cdfDist);

        // 5. TF distance with L2 cyclic shift (global shape check)
        double tfDist = GeometryUtils.tfDistance(tfDrawn, tfTpl, template.allowMirror());
        float tfScore = (float) Math.exp(-LAMBDA_TF * tfDist);

        // 6. Corner matching (topological validation)
        float cornerScore = matchCorners(drawnCorners, tplCorners, contour.length());

        // 7. CDF skewness — asymmetry check (water vs fire key discriminator)
        double skewDrawn = computeSkewness(cdfDrawn);
        double skewTpl = computeSkewness(cdfTpl);
        float skewScore = (float) Math.max(0, 1.0 - Math.abs(skewDrawn - skewTpl) / 1.5);

        // 8. Pixel count ratio — simple area similarity check
        int countDrawn = countPixels(drawn);
        int countTpl = countPixels(template.pattern());
        float pixelRatio = (float) Math.min(countDrawn, countTpl)
                         / Math.max(countDrawn, countTpl);

        // 9. Weighted-sum ensemble
        float confidence = 0.32f * curvScore
                         + 0.32f * cdfScore
                         + 0.18f * tfScore
                         + 0.08f * cornerScore
                         + 0.06f * skewScore
                         + 0.04f * pixelRatio;

        return new MatchResult(confidence, 0f, false, 1f, curvScore);
    }

    private static int countPixels(int[][] img) {int c=0;for(int[]r:img)for(int v:r)if(v!=0)c++;return c;}

    /** Computes skewness of a 1D array (measure of asymmetry). */
    private static double computeSkewness(double[] arr) {
        int n = arr.length;
        double mean = 0;
        for (double v : arr) mean += v;
        mean /= n;
        double m2 = 0, m3 = 0;
        for (double v : arr) {
            double d = v - mean;
            m2 += d * d;
            m3 += d * d * d;
        }
        m2 /= n; m3 /= n;
        double std = Math.sqrt(m2);
        if (std < 1e-9) return 0;
        return m3 / (std * std * std);
    }

    /**
     * Matches two corner sets by finding the optimal shift.
     * Returns the fraction of corners that match, capped at 1.0.
     */
    private static float matchCorners(List<Corner> drawn, List<Corner> template, int contourLen) {
        if (drawn.isEmpty() && template.isEmpty()) return 1f;
        if (drawn.isEmpty() || template.isEmpty()) return 0f;
        if (contourLen <= 0) return 0f;

        int nDrawn = drawn.size(), nTpl = template.size();
        float bestRatio = 0f;

        // Try all shifts (the TF shift is unknown to us here, but corners are sparse)
        for (int shift = 0; shift < contourLen; shift += Math.max(1, contourLen / 16)) {
            int matched = 0;
            for (Corner dc : drawn) {
                int si = (dc.idx() + shift) % contourLen;
                for (Corner tc : template) {
                    int diff = Math.abs(si - tc.idx());
                    diff = Math.min(diff, contourLen - diff);
                    if (diff <= CORNER_WINDOW) { matched++; break; }
                }
            }
            float ratio = (float) matched / Math.max(nDrawn, nTpl);
            if (ratio > bestRatio) bestRatio = ratio;
        }

        return Math.min(bestRatio, 1f);
    }
}
