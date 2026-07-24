package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.compile.operator.CompiledOp;

public sealed interface OpInput permits OpInput.Rune, OpInput.Op {
    record Rune(PositionedGlyph glyph) implements OpInput {
        public String symbolName() {
            return glyph.symbolId().getPath();
        }
    }

    record Op(CompiledOp operator) implements OpInput {}
}
