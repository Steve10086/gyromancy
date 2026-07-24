package com.astune.gyromancy.symbol;

/** Drain parameter rune. */
public final class DrainSymbol extends ParameterSymbol {
    public static final DrainSymbol INSTANCE = new DrainSymbol();

    private DrainSymbol() { super("drain", SkeletonMatcher.DEFAULT_THRESHOLDS); }
}
