package com.astune.gyromancy.symbol;

public class LoopSymbol extends ParameterSymbol {
    public static final LoopSymbol INSTANCE = new LoopSymbol();

    protected LoopSymbol() { super("loop", SkeletonMatcher.DEFAULT_THRESHOLDS); }


}
