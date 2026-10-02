package com.astune.gyromancy.symbol;

/** Earth-element center symbol. */
public final class EarthSymbol extends CenterSymbol {

    private static final SkeletonMatcher.SoftThresholds THRESHOLDS =
            new SkeletonMatcher.SoftThresholds(1, 0.2, 0.99, 0.99, 0.6);

    public static final EarthSymbol INSTANCE = new EarthSymbol();
    private EarthSymbol() { super("earth", 4, false, 0xFF8B4513, THRESHOLDS); }
}
