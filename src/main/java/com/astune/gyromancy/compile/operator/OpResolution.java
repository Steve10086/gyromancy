package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.array.compile.GroupNode;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;

import java.util.Objects;

/**
 * Complete result of forwarding a nested operator.  A replacement operator
 * alone is insufficient because its source array and runtime ownership may
 * also change.
 */
public record OpResolution(
        CompiledOp operator,
        GroupNode sourceGroup,
        ArrayObject array,
        OpRuntimeContext runtimeContext
) {
    public OpResolution {
        operator = Objects.requireNonNull(operator, "operator");
    }

    public static OpResolution unchanged(CompiledOp operator, OpResolveContext context) {
        return new OpResolution(operator, context == null ? null : context.sourceGroup(),
                context == null ? null : context.array(),
                context == null ? null : context.runtimeContext());
    }

    /** Forwards all available ownership/source information to another pass. */
    public OpResolveContext forwardedContext(OpResolveContext fallback) {
        OpResolveContext base = fallback == null
                ? OpResolveContext.forCompile(operator.boundary()) : fallback;
        return new OpResolveContext(
                base.phase(), base.useSite(), operator,
                base.parentOp(), base.parentId(), base.targetBoundary(),
                runtimeContext == null ? base.runtimeContext() : runtimeContext,
                array == null ? base.array() : array,
                sourceGroup == null ? base.sourceGroup() : sourceGroup);
    }

    /** Returns the forwarded runtime context and applies a forwarded array when present. */
    public OpRuntimeContext runtimeContextOr(OpRuntimeContext fallback) {
        OpRuntimeContext result = runtimeContext == null ? fallback : runtimeContext;
        if (result == null || array == null || result.array() == array) return result;
        return result.withArray(array, array.rootCircleGlyph());
    }
}
