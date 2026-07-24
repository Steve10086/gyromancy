package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import com.astune.gyromancy.array.runtime.RuntimeHandle;

import java.util.Map;

public interface PersistentOp extends CompiledOp {
    RuntimeHandle activate(OpRuntimeContext ctx);

    void deactivate(OpRuntimeContext ctx, Map<String, Object> scratchData);
}
