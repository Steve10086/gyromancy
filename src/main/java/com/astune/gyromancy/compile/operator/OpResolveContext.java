package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.GroupNode;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import net.minecraft.resources.ResourceLocation;

/**
 * Context supplied when a parent asks a nested operator to expose its
 * effective operator and array source.  The same contract is used by
 * compile-side projections and runtime activation; the phase and use site
 * keep those two consumers explicit without coupling the compiler to a level.
 */
public record OpResolveContext(
        Phase phase,
        UseSite useSite,
        CompiledOp candidate,
        CompiledOp parentOp,
        ResourceLocation parentId,
        PositionedGlyph targetBoundary,
        OpRuntimeContext runtimeContext,
        ArrayObject array,
        GroupNode sourceGroup
) {
    public enum Phase {
        COMPILE,
        RUNTIME
    }

    public enum UseSite {
        /** A normal parent-group input match. */
        GROUP_INPUT,
        VECTOR,
        ENTITY_EMISSION,
        ENTITY_PAYLOAD,
        PERSISTENT_CHILD,
        DISCARD
    }

    public OpResolveContext {
        phase = phase == null ? Phase.RUNTIME : phase;
        useSite = useSite == null ? UseSite.GROUP_INPUT : useSite;
        parentId = parentId != null ? parentId : parentOp == null ? null : parentOp.id();
        array = array != null
                ? array
                : runtimeContext == null ? null : runtimeContext.array();
    }

    public static OpResolveContext forVector(PositionedGlyph targetBoundary) {
        return new OpResolveContext(Phase.COMPILE, UseSite.VECTOR, null,
                null, null, targetBoundary, null, null, null);
    }

    /** Creates the context used while a normal parent group matches inputs. */
    public static OpResolveContext forCompile(PositionedGlyph targetBoundary) {
        return new OpResolveContext(Phase.COMPILE, UseSite.GROUP_INPUT, null,
                null, null, targetBoundary, null, null, null);
    }

    public static OpResolveContext forRuntime(
            CompiledOp parentOp,
            UseSite useSite,
            OpRuntimeContext runtimeContext,
            PositionedGlyph targetBoundary
    ) {
        return new OpResolveContext(Phase.RUNTIME, useSite, null, parentOp,
                parentOp == null ? null : parentOp.id(), targetBoundary,
                runtimeContext, runtimeContext == null ? null : runtimeContext.array(), null);
    }

    public OpResolveContext withCandidate(CompiledOp candidate) {
        return new OpResolveContext(phase, useSite, candidate, parentOp, parentId,
                targetBoundary, runtimeContext, array, sourceGroup);
    }

    public OpResolveContext withArray(ArrayObject array) {
        return new OpResolveContext(phase, useSite, candidate, parentOp, parentId,
                targetBoundary, runtimeContext, array, sourceGroup);
    }

    public OpResolveContext withSourceGroup(GroupNode sourceGroup) {
        return new OpResolveContext(phase, useSite, candidate, parentOp, parentId,
                targetBoundary, runtimeContext, array, sourceGroup);
    }

    public OpResolveContext withTargetBoundary(PositionedGlyph targetBoundary) {
        return new OpResolveContext(phase, useSite, candidate, parentOp, parentId,
                targetBoundary, runtimeContext, array, sourceGroup);
    }

    public OpResolveContext withRuntimeContext(OpRuntimeContext runtimeContext) {
        return new OpResolveContext(phase, useSite, candidate, parentOp, parentId,
                targetBoundary, runtimeContext, array, sourceGroup);
    }
}
