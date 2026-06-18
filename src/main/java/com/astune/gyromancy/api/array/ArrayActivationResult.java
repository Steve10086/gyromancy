package com.astune.gyromancy.api.array;

import com.astune.gyromancy.api.symbol.SymbolMatch;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Result of attempting to activate a magic array from recognized symbols.
 */
public record ArrayActivationResult(
        boolean success,
        /** The recognized center symbol ID if successful */
        SymbolMatch centerSymbol,
        /** Resolved parameter runes */
        List<SymbolMatch> parameterRunes,
        /** Error message if activation failed */
        Component errorMessage
) {
    public static ArrayActivationResult success(SymbolMatch centerSymbol, List<SymbolMatch> runes) {
        return new ArrayActivationResult(true, centerSymbol, runes, null);
    }

    public static ArrayActivationResult failure(Component errorMessage) {
        return new ArrayActivationResult(false, null, List.of(), errorMessage);
    }
}
