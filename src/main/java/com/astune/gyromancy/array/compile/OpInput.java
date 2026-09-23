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

    record Op(CompiledOp operator, GroupNode sourceGroup, boolean deferred) implements OpInput {
        public Op(CompiledOp operator) {
            this(operator, null, false);
        }

        public Op(CompiledOp operator, GroupNode sourceGroup) {
            this(operator, sourceGroup, false);
        }

        /**
         * Whether this child still carries a dynamically preserved structure
         * (for example a Wireless publisher placeholder). A deferred child is
         * accepted by any matcher so the parent can be built now and resolved
         * by the dynamic structure pass later.
         */
        public Op asDeferred() {
            return deferred ? this : new Op(operator, sourceGroup, true);
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
