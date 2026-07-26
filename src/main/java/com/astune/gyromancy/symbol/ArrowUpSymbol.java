package com.astune.gyromancy.symbol;

/** Upward arrow rune. Split emission uses the array face normal as its direction. */
public final class ArrowUpSymbol extends ParameterSymbol {
    private static final SkeletonMatcher.SoftThresholds THRESHOLDS =
            new SkeletonMatcher.SoftThresholds(0.70, 0.70, 0.70, 0.70, 0.5);

    public static final ArrowUpSymbol INSTANCE = new ArrowUpSymbol();

    private ArrowUpSymbol() { super("arrow_up", THRESHOLDS); }
}
