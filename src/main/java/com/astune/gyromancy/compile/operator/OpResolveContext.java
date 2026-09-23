package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.GroupNode;
import net.minecraft.resources.ResourceLocation;

/** Structure/local matching context; it contains no runtime ownership state. */
public record OpResolveContext(
        Phase phase,
        UseSite useSite,
        CompiledOp candidate,
        CompiledOp parentOp,
        ResourceLocation parentId,
        PositionedGlyph targetBoundary,
        GroupNode sourceGroup
) {
    public enum Phase {
        STATIC,
        LOCAL,
        /** @deprecated Use {@link #STATIC}. */
        @Deprecated(forRemoval = false)
        COMPILE
    }

    public enum UseSite {
        GROUP_INPUT,
        VECTOR,
        ENTITY_EMISSION,
        ENTITY_PAYLOAD,
        PERSISTENT_CHILD,
        DISCARD
    }

    public OpResolveContext {
        phase = phase == null ? Phase.STATIC : phase;
        useSite = useSite == null ? UseSite.GROUP_INPUT : useSite;
        parentId = parentId != null ? parentId : parentOp == null ? null : parentOp.id();
    }

    public static OpResolveContext forVector(PositionedGlyph targetBoundary) {
        return new OpResolveContext(Phase.STATIC, UseSite.VECTOR, null,
                null, null, targetBoundary, null);
    }

    public static OpResolveContext forCompile(PositionedGlyph targetBoundary) {
        return new OpResolveContext(Phase.COMPILE, UseSite.GROUP_INPUT, null,
                null, null, targetBoundary, null);
    }

    public static OpResolveContext forStatic(PositionedGlyph targetBoundary) {
        return new OpResolveContext(Phase.STATIC, UseSite.GROUP_INPUT, null,
                null, null, targetBoundary, null);
    }

    public static OpResolveContext forLocal(PositionedGlyph targetBoundary) {
        return new OpResolveContext(Phase.LOCAL, UseSite.GROUP_INPUT, null,
                null, null, targetBoundary, null);
    }

    public OpResolveContext withCandidate(CompiledOp candidate) {
        return new OpResolveContext(phase, useSite, candidate, parentOp, parentId,
                targetBoundary, sourceGroup);
    }

    public OpResolveContext withSourceGroup(GroupNode sourceGroup) {
        return new OpResolveContext(phase, useSite, candidate, parentOp, parentId,
                targetBoundary, sourceGroup);
    }

    public OpResolveContext withTargetBoundary(PositionedGlyph targetBoundary) {
        return new OpResolveContext(phase, useSite, candidate, parentOp, parentId,
                targetBoundary, sourceGroup);
    }
}
