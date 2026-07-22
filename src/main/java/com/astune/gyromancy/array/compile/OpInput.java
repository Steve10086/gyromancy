package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.symbol.PositionedGlyph;

public sealed interface OpInput permits OpInput.Rune, OpInput.Op {
    record Rune(PositionedGlyph glyph) implements OpInput {
        public String symbolName() {
            return glyph.symbolId().getPath();
        }
    }

    record Op(CompiledArrayNode node) implements OpInput {}
}
