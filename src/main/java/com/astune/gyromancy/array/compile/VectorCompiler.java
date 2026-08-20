package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.compile.operator.CompiledOp;

import java.util.Collection;
import java.util.List;

/**
 * Explicit second-pass compiler for vector structures. It is intentionally
 * independent from any parent Op. A parent chooses when to invoke it and
 * supplies the exact matching definition set it wants to use.
 */
public final class VectorCompiler extends GroupCompiler {
    public static final int MAX_DEPTH = 1;

    public VectorCompiler(Collection<? extends OpDefinition> definitions) {
        super(definitions, MAX_DEPTH);
    }

    public CompileResult<CompiledOp> compile(OpInput.RawGroup rawGroup) {
        if (rawGroup == null || rawGroup.boundary() == null
                || rawGroup.boundary().role() != SymbolRole.OUTER_CIRCLE) {
            return new CompileResult.Failure<>(List.of(new CompileDiagnostic(
                    "invalid_vector_boundary", "Vector compilation requires a circle boundary")));
        }
        return compile(rawGroup.group());
    }

    /** Compiles either a preserved raw group or the source group of a compiled child. */
    public CompileResult<CompiledOp> compile(OpInput input) {
        if (input instanceof OpInput.RawGroup rawGroup) return compile(rawGroup);
        if (input instanceof OpInput.Op op && op.sourceGroup() != null) {
            return compile(new OpInput.RawGroup(op.sourceGroup(), List.of()));
        }
        return new CompileResult.Failure<>(List.of(new CompileDiagnostic(
                "missing_vector_source_group", "Vector compilation requires a group input")));
    }

}
