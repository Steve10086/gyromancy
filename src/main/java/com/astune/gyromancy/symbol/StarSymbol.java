package com.astune.gyromancy.symbol;

/** Star center symbol — allows rotation. */
public final class StarSymbol extends CenterSymbol {
    public static final StarSymbol INSTANCE = new StarSymbol();
    private StarSymbol() { super("star", 5, true, 0xFFFFFF88); }
}
