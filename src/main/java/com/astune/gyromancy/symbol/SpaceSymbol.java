package com.astune.gyromancy.symbol;

/** Space-element center symbol. */
public final class SpaceSymbol extends CenterSymbol {
    public static final SpaceSymbol INSTANCE = new SpaceSymbol();

    private SpaceSymbol() { super("space", 0, false, 0xFF7AA7FF); }
}
