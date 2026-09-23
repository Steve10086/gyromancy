package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.symbol.PositionedGlyph;

import java.util.Objects;
import java.util.Set;

/** Structure-only forwarding data produced by a local compilation step. */
public record LocalForwardContext(
        SourceFrameRef frame,
        PositionedGlyph sourceRoot,
        Set<String> dependencies
) {
    public LocalForwardContext {
        dependencies = Set.copyOf(dependencies);
    }
}
