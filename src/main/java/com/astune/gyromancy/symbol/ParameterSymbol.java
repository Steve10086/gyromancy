package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.symbol.SymbolRole;

/** Base for parameter runes placed between the circle and center symbol. */
public abstract class ParameterSymbol extends Symbol {
    protected ParameterSymbol(String name, SkeletonMatcher.SoftThresholds thresholds) {
        super(name, thresholds);
    }

    @Override
    public SymbolRole role() { return SymbolRole.PARAMETER_RUNE; }
}
