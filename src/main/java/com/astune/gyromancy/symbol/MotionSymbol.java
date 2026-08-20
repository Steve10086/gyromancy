package com.astune.gyromancy.symbol;

/** Entry rune for a MomentumOp motion plan. */
public final class MotionSymbol extends ParameterSymbol {
    public static final MotionSymbol INSTANCE = new MotionSymbol();

    private MotionSymbol() {
        super("motion", SkeletonMatcher.DEFAULT_THRESHOLDS);
    }
}
