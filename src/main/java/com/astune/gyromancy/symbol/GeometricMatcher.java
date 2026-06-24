package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.symbol.SymbolTemplate;

/**
 * Thin wrapper around {@link SkeletonMatcher} for backward compatibility.
 *
 * <p>Delegates all matching to the skeleton-graph pipeline.
 */
public final class GeometricMatcher {

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

    /** Matches a drawn image against a single template. */
    public static MatchResult match(int[][] drawn, SymbolTemplate template) {
        float conf = SkeletonMatcher.getInstance().matchOne(drawn, template.id());
        return new MatchResult(conf, 0f, false, 1f, conf);
    }
}
