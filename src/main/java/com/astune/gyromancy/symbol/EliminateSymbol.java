package com.astune.gyromancy.symbol;

/** Eliminate parameter rune. */
public final class EliminateSymbol extends ParameterSymbol {
    public static final EliminateSymbol INSTANCE = new EliminateSymbol();

    private EliminateSymbol() { super("eliminate", SkeletonMatcher.DEFAULT_THRESHOLDS); }
}
