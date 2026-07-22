package com.astune.gyromancy.symbol;

/** Fix parameter rune. */
public final class FixSymbol extends ParameterSymbol {
    private static final SkeletonMatcher.SoftThresholds THRESHOLDS =
            new SkeletonMatcher.SoftThresholds(0.5, 0.5, 0.5, 0.95, 0.9);

    public static final FixSymbol INSTANCE = new FixSymbol();

    private FixSymbol() { super("fix", THRESHOLDS); }
}
