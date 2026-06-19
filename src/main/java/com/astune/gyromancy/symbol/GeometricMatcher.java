package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.symbol.SymbolTemplate;
import com.astune.gyromancy.util.GeometryUtils;

/**
 * Core geometric pattern matching engine.
 * Compares a normalized drawn symbol against a template using:
 * <ul>
 *   <li>Hu invariant moments (rotation/scale/mirror invariant)</li>
 *   <li>PCA-based rotation estimation</li>
 *   <li>Pixel overlap scoring</li>
 *   <li>Sobel edge direction histogram comparison</li>
 * </ul>
 *
 * <p>Weighted score: 0.35×Hu + 0.40×overlap + 0.25×edge = final confidence (0–1).
 */
public final class GeometricMatcher {

    private GeometricMatcher() {}

    /**
     * Result of matching one drawn symbol against one template.
     */
    public record MatchResult(
            /** Overall confidence 0.0–1.0 */
            float confidence,
            /** Estimated rotation in degrees (0–360) */
            float rotationDegrees,
            /** Whether the drawn symbol appears mirrored */
            boolean mirrored,
            /** Estimated scale ratio relative to template */
            float scale,
            /** Individual Hu moment score (0–1) */
            float huScore,
            /** Individual pixel overlap score (0–1) */
            float overlapScore,
            /** Individual edge histogram score (0–1) */
            float edgeScore
    ) {
        /** A null-match sentinel */
        public static final MatchResult NONE = new MatchResult(0f, 0f, false, 1f, 0f, 0f, 0f);
    }

    /**
     * Weight configuration for the three scoring components.
     */
    public record MatchWeights(float huWeight, float overlapWeight, float edgeWeight) {
        public static final MatchWeights DEFAULT = new MatchWeights(0.35f, 0.40f, 0.25f);
    }

    /**
     * Matches a drawn symbol (normalized 32×32 binary) against a template.
     *
     * @param drawnPixels normalized binary image, [y][x], non-zero = foreground
     * @param template    the symbol template to match against
     * @return MatchResult with confidence score and geometric parameters
     */
    public static MatchResult match(int[][] drawnPixels, SymbolTemplate template) {
        return match(drawnPixels, template, MatchWeights.DEFAULT);
    }

    /**
     * Matches with custom scoring weights.
     */
    public static MatchResult match(int[][] drawnPixels, SymbolTemplate template, MatchWeights weights) {
        // 1. Compute features on the drawn symbol
        double[] drawnHu = GeometryUtils.computeHuMoments(drawnPixels);
        int[] drawnEdge = GeometryUtils.computeEdgeHistogram(drawnPixels);

        // 2. Compute features on the template
        double[] templateHu = GeometryUtils.computeHuMoments(template.pattern());
        int[] templateEdge = GeometryUtils.computeEdgeHistogram(template.pattern());

        // 3. Estimate rotation via PCA
        GeometryUtils.PCAResult pca = GeometryUtils.computePCA(drawnPixels);
        float rotationDeg = pca.angleDegrees();

        // 4. Score — try normal orientation first
        MatchResult normalResult = scoreMatch(
                drawnPixels, template.pattern(),
                drawnHu, templateHu,
                drawnEdge, templateEdge,
                rotationDeg, false, weights
        );

        // 5. If mirroring allowed, try mirrored orientation
        if (template.allowMirror()) {
            int[][] mirroredPixels = mirrorImage(drawnPixels);
            double[] mirroredHu = GeometryUtils.computeHuMoments(mirroredPixels);
            int[] mirroredEdge = GeometryUtils.computeEdgeHistogram(mirroredPixels);

            MatchResult mirroredResult = scoreMatch(
                    mirroredPixels, template.pattern(),
                    mirroredHu, templateHu,
                    mirroredEdge, templateEdge,
                    rotationDeg, true, weights
            );

            if (mirroredResult.confidence() > normalResult.confidence()) {
                return mirroredResult;
            }
        }

        return normalResult;
    }

    private static MatchResult scoreMatch(
            int[][] drawnPixels, int[][] templatePixels,
            double[] drawnHu, double[] templateHu,
            int[] drawnEdge, int[] templateEdge,
            float rotationDeg, boolean mirrored,
            MatchWeights weights) {

        // Hu moment distance → score
        double huDist = GeometryUtils.huDistance(drawnHu, templateHu);
        float huScore = (float) GeometryUtils.huScore(huDist);

        // Edge histogram distance → score
        double edgeDist = GeometryUtils.edgeHistogramDistance(drawnEdge, templateEdge);
        float edgeScore = (float) GeometryUtils.edgeScore(edgeDist);

        // Pixel overlap score
        float overlapScore;
        if (drawnPixels.length == templatePixels.length
                && drawnPixels[0].length == templatePixels[0].length) {
            overlapScore = (float) GeometryUtils.overlapScore(drawnPixels, templatePixels);
        } else {
            // Resize for comparison
            int[][] normalized = GeometryUtils.normalize(
                    drawnPixels,
                    templatePixels[0].length,
                    templatePixels.length
            );
            overlapScore = (float) GeometryUtils.overlapScore(normalized, templatePixels);
        }

        // Weighted confidence
        float confidence = weights.huWeight() * huScore
                + weights.overlapWeight() * overlapScore
                + weights.edgeWeight() * edgeScore;

        // Estimate scale from bounding box ratio
        int[] drawnBbox = GeometryUtils.computeBoundingBox(drawnPixels);
        int[] templateBbox = GeometryUtils.computeBoundingBox(templatePixels);
        int drawnW = drawnBbox[2] - drawnBbox[0] + 1;
        int templateW = templateBbox[2] - templateBbox[0] + 1;
        float scale = templateW > 0 ? (float) drawnW / templateW : 1f;

        return new MatchResult(
                Math.clamp(confidence, 0f, 1f),
                rotationDeg, mirrored, scale,
                huScore, overlapScore, edgeScore
        );
    }

    /**
     * Horizontally mirrors a binary image.
     */
    static int[][] mirrorImage(int[][] image) {
        int h = image.length;
        int w = h > 0 ? image[0].length : 0;
        int[][] mirrored = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                mirrored[y][w - 1 - x] = image[y][x];
            }
        }
        return mirrored;
    }
}
