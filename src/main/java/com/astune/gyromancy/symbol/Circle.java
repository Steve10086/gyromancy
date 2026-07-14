package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.symbol.SymbolRole;

/** Base for outer-circle symbols (enclosing boundary of a magic array). */
public abstract class Circle extends Symbol {
    protected Circle(String name, SkeletonMatcher.SoftThresholds thresholds) {
        super(name, thresholds);
    }

    @Override
    public SymbolRole role() { return SymbolRole.OUTER_CIRCLE; }
}
