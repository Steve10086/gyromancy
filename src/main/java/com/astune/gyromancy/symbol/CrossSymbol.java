package com.astune.gyromancy.symbol;

/** Cross parameter rune. */
public final class CrossSymbol extends ParameterSymbol {
    private static final SkeletonMatcher.SoftThresholds THRESHOLDS =
            new SkeletonMatcher.SoftThresholds(1, 0.6, 0.90, 0.8, 0.6);

    public static final CrossSymbol INSTANCE = new CrossSymbol();

    private CrossSymbol() { super("cross", SkeletonMatcher.DEFAULT_THRESHOLDS); }
}
