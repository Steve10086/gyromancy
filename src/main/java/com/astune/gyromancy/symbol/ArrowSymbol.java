package com.astune.gyromancy.symbol;

/** Direction-indicating arrow rune. Guides projectiles. */
public final class ArrowSymbol extends ParameterSymbol {
    private static final SkeletonMatcher.SoftThresholds THRESHOLDS =
            new SkeletonMatcher.SoftThresholds(0.70, 0.70, 0.70, 0.70, 0.75);

    public static final ArrowSymbol INSTANCE = new ArrowSymbol();

    private ArrowSymbol() { super("arrow", THRESHOLDS); }
}
