package com.astune.gyromancy.symbol;

/** Engaging parameter rune. */
public final class EngagingSymbol extends ParameterSymbol {
    public static final EngagingSymbol INSTANCE = new EngagingSymbol();

    private EngagingSymbol() { super("engaging", SkeletonMatcher.DEFAULT_THRESHOLDS); }
}
