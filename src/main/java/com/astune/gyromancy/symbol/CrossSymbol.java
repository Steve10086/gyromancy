package com.astune.gyromancy.symbol;

/** Cross parameter rune. */
public final class CrossSymbol extends ParameterSymbol {
    public static final CrossSymbol INSTANCE = new CrossSymbol();

    private CrossSymbol() { super("cross", SkeletonMatcher.DEFAULT_THRESHOLDS); }
}
