package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.symbol.SymbolTemplate;
import com.astune.gyromancy.util.GeometryUtils;

/**
 * Shape matching via Fourier Descriptors of contour.
 *
 * <p>Fourier Descriptors encode the outline of a shape as 7 rotation-invariant,
 * scale-invariant real-valued coefficients. Matching is L2 distance between
 * descriptor vectors.
 */
public final class GeometricMatcher {

    private GeometricMatcher() {}

    public record MatchResult(
            float confidence,
            float rotationDegrees,   // not used by FD (rotation-invariant)
            boolean mirrored,
            float scale,
            float fdScore
    ) {
        public static final MatchResult NONE = new MatchResult(0f, 0f, false, 1f, 0f);
    }

    public static MatchResult match(int[][] drawnPixels, SymbolTemplate template) {
        float[] drawnFD = GeometryUtils.fourierDescriptor(drawnPixels);
        float[] tplFD = template.fourierDescriptor();

        float dist = GeometryUtils.fdDistance(drawnFD, tplFD);
        float score = GeometryUtils.fdScore(dist);

        boolean mirrored = false;
        if (template.allowMirror()) {
            int[][] mirroredImg = GeometryUtils.mirrorImage(drawnPixels);
            float[] mirrorFD = GeometryUtils.fourierDescriptor(mirroredImg);
            float mirrorDist = GeometryUtils.fdDistance(mirrorFD, tplFD);
            float mirrorScore = GeometryUtils.fdScore(mirrorDist);
            if (mirrorScore > score) { score = mirrorScore; mirrored = true; }
        }

        return new MatchResult(score, 0f, mirrored, 1f, score);
    }
}
