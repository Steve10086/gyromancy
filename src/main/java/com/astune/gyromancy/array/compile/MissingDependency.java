package com.astune.gyromancy.array.compile;

import java.util.Objects;
import java.util.UUID;

public record MissingDependency(String key, UUID consumerGlyph) {
    public MissingDependency {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(consumerGlyph, "consumerGlyph");
    }
}
