package com.astune.gyromancy.array.runtime;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.array.compile.CompiledArray;
import net.minecraft.server.level.ServerLevel;

public final class ArrayRuntimes {
    private ArrayRuntimes() {}

    public static RuntimeHandle activate(CompiledArray compiled, ServerLevel level) {
        return LegacyRuntimeAdapter.activate(compiled, level);
    }

    public static void deactivate(ServerLevel level, ArrayObject array) {
        LegacyRuntimeAdapter.deactivate(level, array);
    }
}
