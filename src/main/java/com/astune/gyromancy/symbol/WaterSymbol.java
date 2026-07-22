package com.astune.gyromancy.symbol;

/** Water-element center symbol. Runtime behavior is provided by WaterProjectileOp. */
public final class WaterSymbol extends CenterSymbol {
    public static final WaterSymbol INSTANCE = new WaterSymbol();
    private WaterSymbol() { super("water", 0, false, 0xFF8888FF); }
}
