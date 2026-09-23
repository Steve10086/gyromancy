package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.geometry.SurfaceFrame;
import com.astune.gyromancy.api.symbol.PositionedGlyph;

import java.util.Objects;

/** Immutable source geometry forwarded by a dynamic structure. */
public record SourceFrameRef(PositionedGlyph rootGlyph, SurfaceFrame frame) {
    public SourceFrameRef {
        Objects.requireNonNull(rootGlyph, "rootGlyph");
        Objects.requireNonNull(frame, "frame");
    }

    public static SourceFrameRef fromGroup(GroupNode group) {
        Objects.requireNonNull(group, "group");
        PositionedGlyph root = Objects.requireNonNull(group.boundary(), "group.boundary");
        return new SourceFrameRef(root, root.surface());
    }
}
