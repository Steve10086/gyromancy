package com.astune.gyromancy.symbol;

/** Split parameter rune. */
public final class SplitSymbol extends ParameterSymbol {
    public static final SplitSymbol INSTANCE = new SplitSymbol();

    private SplitSymbol() { super("split", SkeletonMatcher.DEFAULT_THRESHOLDS); }
}
