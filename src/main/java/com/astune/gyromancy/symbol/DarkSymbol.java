package com.astune.gyromancy.symbol;

/** Dark/void-element center symbol. */
public final class DarkSymbol extends CenterSymbol {
    public static final DarkSymbol INSTANCE = new DarkSymbol();
    private DarkSymbol() { super("dark", 0, false, 0xFF121116); }
}
