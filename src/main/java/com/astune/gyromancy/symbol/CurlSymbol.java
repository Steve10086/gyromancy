package com.astune.gyromancy.symbol;

/** Curl parameter rune. */
public final class CurlSymbol extends ParameterSymbol {
    public static final CurlSymbol INSTANCE = new CurlSymbol();

    private CurlSymbol() { super("curl", SkeletonMatcher.DEFAULT_THRESHOLDS); }
}
