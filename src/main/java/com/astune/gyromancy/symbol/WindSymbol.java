package com.astune.gyromancy.symbol;

/** Wind-element center symbol — allows rotation. */
public final class WindSymbol extends CenterSymbol {
    public static final WindSymbol INSTANCE = new WindSymbol();
    private WindSymbol() { super("wind", 0, true, 0xFF88FFFF); }
}
