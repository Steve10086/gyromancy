package com.astune.gyromancy.symbol;

/** Fire-element center symbol. Runtime behavior is provided by FireballOp. */
public final class FireSymbol extends CenterSymbol {
    public static final FireSymbol INSTANCE = new FireSymbol();

    private FireSymbol() { super("fire", 3, false, 0xFFFF8888); }
}
