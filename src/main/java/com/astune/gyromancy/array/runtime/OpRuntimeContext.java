package com.astune.gyromancy.array.runtime;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.geometry.SurfaceFrame;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.compile.operator.CompiledOp;
import com.astune.gyromancy.entity.ArrayRelativePosition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Runtime inputs shared by a persistent operator and any nested operators it activates. */
public record OpRuntimeContext(
        ServerLevel level,
        CompiledOp op,
        Vec3 origin,
        Vec3 normal,
        ArrayObject array,
        PositionedGlyph arrayRootGlyph,
        SurfaceFrame activationFrame,
        Object parent,
        boolean assignParent
) {
    public static OpRuntimeContext empty() {
        return new OpRuntimeContext(null, null);
    }

    public OpRuntimeContext(ServerLevel level, CompiledOp op, Vec3 origin, Vec3 normal) {
        this(level, op, origin, normal, null, null, null, null, true);
    }

    public OpRuntimeContext(ServerLevel level, CompiledOp op) {
        this(level, op, null, null, null, null, null, null, true);
    }

    public OpRuntimeContext at(Vec3 origin, Vec3 normal) {
        return new OpRuntimeContext(level, op, origin, normal, array, arrayRootGlyph,
                activationFrame, parent, assignParent);
    }

    public OpRuntimeContext forOp(CompiledOp nestedOp) {
        return new OpRuntimeContext(level, nestedOp, origin, normal, array, arrayRootGlyph,
                activationFrame, parent, assignParent);
    }

    /** Adds the live array plus the root frame captured when the runtime was compiled. */
    public OpRuntimeContext withArray(ArrayObject array, PositionedGlyph arrayRootGlyph) {
        SurfaceFrame captured = activationFrame != null
                ? activationFrame
                : array == null ? null : array.rootCircleGlyph().surface();
        Object resolvedParent = assignParent && parent == null ? array : parent;
        return new OpRuntimeContext(level, op, origin, normal, array, arrayRootGlyph,
                captured, resolvedParent, assignParent);
    }

    /** Overrides the object whose activation owns this runtime. */
    public OpRuntimeContext withParent(Object parent) {
        return new OpRuntimeContext(level, op, origin, normal, array, arrayRootGlyph,
                activationFrame, parent, true);
    }

    /** Prevents newly spawned entities from receiving an activation parent. */
    public OpRuntimeContext withoutParent() {
        return new OpRuntimeContext(level, op, origin, normal, array, arrayRootGlyph,
                activationFrame, null, false);
    }

    public SurfaceFrame compileFrame() {
        return arrayRootGlyph == null ? null : arrayRootGlyph.surface();
    }

    public SurfaceFrame liveFrame() {
        return array == null ? null : array.rootCircleGlyph().surface();
    }

    /** Returns the current geometry for a compiled glyph when the array refreshed it. */
    public PositionedGlyph liveGlyph(PositionedGlyph glyph) {
        if (array == null) return glyph;
        if (array.rootCircleGlyph().glyphUuid().equals(glyph.glyphUuid())) {
            return array.rootCircleGlyph();
        }
        return array.boundGlyphs().stream()
                .filter(bound -> bound.glyphUuid().equals(glyph.glyphUuid()))
                .findFirst()
                .orElse(glyph);
    }

    /**
     * Resolves a compiled glyph's position into the live array frame. Explicit
     * activation positions still take precedence for hooks such as on-discard.
     */
    public Vec3 positionFor(PositionedGlyph glyph) {
        if (origin != null) return origin;
        if (array == null || arrayRootGlyph == null) return glyph.center();

        ArrayRelativePosition relative = ArrayRelativePosition.capture(
                glyph.center(), arrayRootGlyph.center(), arrayRootGlyph.surface());
        PositionedGlyph liveRoot = array.rootCircleGlyph();
        return relative.resolve(liveRoot.center(), liveRoot.surface());
    }

    /** Uses the current array plane for nested effects while it is available. */
    public Vec3 normalFor(PositionedGlyph glyph) {
        if (normal != null) return normal;
        return array == null ? glyph.surface().normal() : array.rootCircleGlyph().surface().normal();
    }

    /**
     * Returns the current array frame for runtime resolution. The supplied
     * glyph is only used as the compile-time fallback when no live array is
     * attached; its refreshed geometry is deliberately not consulted here.
     */
    public SurfaceFrame frameFor(PositionedGlyph glyph) {
        if (array == null || arrayRootGlyph == null) return glyph.surface();
        return array.rootCircleGlyph().surface();
    }
}
