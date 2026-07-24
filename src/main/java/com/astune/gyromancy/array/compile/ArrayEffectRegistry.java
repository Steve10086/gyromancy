package com.astune.gyromancy.array.compile;

import java.util.List;

@Deprecated(forRemoval = false)
public final class ArrayEffectRegistry {
    private ArrayEffectRegistry() {}

    public static List<OpDefinition> effects() {
        return OpDefinitionRegistry.definitions();
    }

    public static void register(OpDefinition definition) {
        OpDefinitionRegistry.register(definition);
    }
}
