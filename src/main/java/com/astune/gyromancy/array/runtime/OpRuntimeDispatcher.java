package com.astune.gyromancy.array.runtime;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.array.compile.CompiledArray;
import com.astune.gyromancy.array.runtime.emit.EmitResult;
import com.astune.gyromancy.compile.operator.CompiledOp;
import com.astune.gyromancy.compile.operator.PersistentOp;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;

public final class OpRuntimeDispatcher {
    private static final String RUNTIME_KEY = "__runtime";
    private static final String COMPILED_OP_KEY = "__compiled_op";

    private OpRuntimeDispatcher() {}

    public static RuntimeHandle activate(CompiledArray compiled, ServerLevel level) {
        RuntimeHandle handle = compiled.root() instanceof PersistentOp persistent
                ? persistent.activate(new OpRuntimeContext(level, compiled.root()))
                : new RuntimeHandle(Map.of());
        Map<String, Object> data = new HashMap<>(handle.scratchData());
        data.put(RUNTIME_KEY, compiled.root().id().toString());
        data.put(COMPILED_OP_KEY, compiled.root());
        return new RuntimeHandle(Map.copyOf(data));
    }

    public static void deactivate(ServerLevel level, ArrayObject array) {
        EmitResult.discardEmittedEntities(level, array.scratchData());
        if (array.scratchData().get(COMPILED_OP_KEY) instanceof CompiledOp op && op instanceof PersistentOp persistent) {
            persistent.deactivate(new OpRuntimeContext(level, op), array.scratchData());
        }
    }
}
