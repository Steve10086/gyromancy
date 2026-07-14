package com.astune.gyromancy.symbol;

/** Outer circle — the enclosing boundary of every magic array. */
public final class CircleOuterSymbol extends Circle {
    private static final SkeletonMatcher.SoftThresholds THRESHOLDS =
            new SkeletonMatcher.SoftThresholds(0.95, 0.95, 0.95, 0.95, 0.95);

    public static final CircleOuterSymbol INSTANCE = new CircleOuterSymbol();

    private CircleOuterSymbol() { super("circle_outer", THRESHOLDS); }
}
