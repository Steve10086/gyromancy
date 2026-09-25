package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.element.ManaElements;
import com.astune.gyromancy.compile.operator.CompiledOp;

import java.util.Set;

/** The phase-two result accepted by the runtime dispatcher. */
public record RuntimeModel(CompiledOp root, Set<String> wirelessDependencyKeys,
                           ManaElements manaElements) {
    public RuntimeModel(CompiledOp root, Set<String> wirelessDependencyKeys) {
        this(root, wirelessDependencyKeys, ManaElements.EMPTY);
    }

    public RuntimeModel {
        wirelessDependencyKeys = Set.copyOf(wirelessDependencyKeys);
        manaElements = manaElements == null ? ManaElements.EMPTY : manaElements;
    }
}
