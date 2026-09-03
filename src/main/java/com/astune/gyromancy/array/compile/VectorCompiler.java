package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.compile.operator.CompiledOp;
import com.astune.gyromancy.compile.operator.OpResolution;
import com.astune.gyromancy.compile.operator.OpResolveContext;
import com.astune.gyromancy.compile.operator.OpResolver;
import com.astune.gyromancy.compile.vector.VectorOp;

import java.util.Collection;
import java.util.List;

/**
 * Explicit second-pass compiler for vector structures. A parent chooses when
 * to invoke it and supplies the exact matching definition set and optional
 * forwarding context it wants to use.
 */
public final class VectorCompiler extends GroupCompiler {
    public static final int MAX_DEPTH = 1;

    public VectorCompiler(Collection<? extends OpDefinition> definitions) {
        super(definitions, MAX_DEPTH);
    }

    public CompileResult<CompiledOp> compile(OpInput.RawGroup rawGroup) {
        return compile(rawGroup, OpResolveContext.forVector(
                rawGroup == null ? null : rawGroup.boundary()));
    }

    public CompileResult<CompiledOp> compile(OpInput.RawGroup rawGroup,
                                             OpResolveContext context) {
        if (rawGroup == null || rawGroup.boundary() == null
                || rawGroup.boundary().role() != SymbolRole.OUTER_CIRCLE) {
            return new CompileResult.Failure<>(List.of(new CompileDiagnostic(
                    "invalid_vector_boundary", "Vector compilation requires a circle boundary")));
        }
        return compile(rawGroup.group(), context);
    }

    /** Compiles either a preserved raw group or the source group of a compiled child. */
    public CompileResult<CompiledOp> compile(OpInput input) {
        PositionedGlyph targetBoundary = input == null ? null : switch (input) {
                case OpInput.RawGroup rawGroup -> rawGroup.boundary();
                case OpInput.Op op -> op.operator().boundary();
                case OpInput.Rune ignored -> null;
            };
        return compile(input, OpResolveContext.forVector(targetBoundary));
    }

    /**
     * Compiles a nested input after giving a resolvable operator the chance to
     * forward its complete source representation.  Ordinary inputs follow the
     * original path unchanged.
     */
    public CompileResult<CompiledOp> compile(OpInput input, OpResolveContext context) {
        if (input instanceof OpInput.Op op) {
            OpResolveContext effectiveContext = context == null
                    ? OpResolveContext.forVector(op.operator().boundary()) : context;
            OpResolution resolution = OpResolver.resolve(input, effectiveContext);
            OpResolveContext forwardedContext = resolution.forwardedContext(effectiveContext);
            if (resolution.sourceGroup() != null) {
                return compile(new OpInput.RawGroup(resolution.sourceGroup(), List.of()),
                        forwardedContext);
            }
            if (resolution.operator() != op.operator()) {
                if (resolution.operator() instanceof VectorOp) {
                    return new CompileResult.Success<>(resolution.operator());
                }
                return new CompileResult.Failure<>(List.of(new CompileDiagnostic(
                        "missing_vector_source_group",
                        "Resolved vector input did not provide a vector or source group")));
            }
        }

        return compileUnresolved(input, context);
    }

    private CompileResult<CompiledOp> compileUnresolved(OpInput input,
                                                        OpResolveContext context) {
        if (input instanceof OpInput.RawGroup rawGroup) return compile(rawGroup, context);
        if (input instanceof OpInput.Op op && op.sourceGroup() != null) {
            return compile(new OpInput.RawGroup(op.sourceGroup(), List.of()), context);
        }
        return new CompileResult.Failure<>(List.of(new CompileDiagnostic(
                "missing_vector_source_group", "Vector compilation requires a group input")));
    }

}
