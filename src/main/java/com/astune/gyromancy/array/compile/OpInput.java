package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.compile.operator.CompiledOp;

import java.util.List;

public sealed interface OpInput permits OpInput.Rune, OpInput.Op, OpInput.RawGroup {
    record Rune(PositionedGlyph glyph) implements OpInput {
        public String symbolName() {
            return glyph.symbolId().getPath();
        }
    }

    record Op(CompiledOp operator, GroupNode sourceGroup) implements OpInput {
        public Op(CompiledOp operator) {
            this(operator, null);
        }
    }

    /**
     * A nested group which is intentionally kept as authored data instead of
     * being compiled into an executable operator. It remains a passive input
     * so operators such as ProjectionOp, ShapeOp, and WirelessOp can consume
     * its boundary and glyph tree without making it executable.
     */
    record RawGroup(GroupNode group, List<CompileDiagnostic> failures) implements OpInput {
        public RawGroup {
            failures = List.copyOf(failures);
        }

        public PositionedGlyph boundary() {
            return group.boundary();
        }
    }
}
