package com.astune.gyromancy.array.compile;

import java.util.Set;

/** A child input after static normalization and its transitive dependencies. */
public record NormalizedChild(OpInput input, Set<String> dependencyKeys) {
    public NormalizedChild {
        dependencyKeys = Set.copyOf(dependencyKeys);
    }
}
