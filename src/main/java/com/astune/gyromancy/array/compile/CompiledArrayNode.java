package com.astune.gyromancy.array.compile;

public sealed interface CompiledArrayNode permits InstantRuntimeNode, FieldRuntimeNode, ProjectileRuntimeNode,
        TriggerRuntimeNode, StorageRuntimeNode {}
