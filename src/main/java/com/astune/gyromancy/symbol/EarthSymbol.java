package com.astune.gyromancy.symbol;

/** Earth-element center symbol. */
public final class EarthSymbol extends CenterSymbol {
    public static final EarthSymbol INSTANCE = new EarthSymbol();
    private EarthSymbol() { super("earth", 4, false, 0xFF8B4513); }
}
