package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.compile.operator.CompiledOp;

import java.util.Set;

/** The phase-two result accepted by the runtime dispatcher. */
public record RuntimeModel(CompiledOp root, Set<String> wirelessDependencyKeys) {
    public RuntimeModel {
        wirelessDependencyKeys = Set.copyOf(wirelessDependencyKeys);
    }
}
