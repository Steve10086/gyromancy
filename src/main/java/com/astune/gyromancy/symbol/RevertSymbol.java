package com.astune.gyromancy.symbol;

/** Revert/reverse parameter rune. */
public final class RevertSymbol extends ParameterSymbol {
    private static final SkeletonMatcher.SoftThresholds THRESHOLDS =
            new SkeletonMatcher.SoftThresholds(0.9, 0.7, 0.8, 0.8, 0.55);

    public static final RevertSymbol INSTANCE = new RevertSymbol();

    private RevertSymbol() { super("revert", THRESHOLDS); }
}
