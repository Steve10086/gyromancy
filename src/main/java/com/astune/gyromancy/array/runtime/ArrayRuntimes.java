package com.astune.gyromancy.array.runtime;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.array.compile.CompiledArray;
import net.minecraft.server.level.ServerLevel;

@Deprecated(forRemoval = false)
public final class ArrayRuntimes {
    private ArrayRuntimes() {}

    public static RuntimeHandle activate(CompiledArray compiled, ServerLevel level) {
        return OpRuntimeDispatcher.activate(compiled, level);
    }

    public static void deactivate(ServerLevel level, ArrayObject array) {
        OpRuntimeDispatcher.deactivate(level, array);
    }
}
