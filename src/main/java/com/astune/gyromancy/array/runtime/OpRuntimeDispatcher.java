package com.astune.gyromancy.array.runtime;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.element.ManaElements;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.ArrayCompilePipeline;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.CompiledArray;
import com.astune.gyromancy.array.compile.RuntimeModel;
import com.astune.gyromancy.compile.operator.CompiledOp;
import com.astune.gyromancy.compile.operator.PersistentOp;
import com.astune.gyromancy.registry.ModAttachments;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;

public final class OpRuntimeDispatcher {
    private static final String RUNTIME_KEY = "__runtime";
    private static final String COMPILED_OP_KEY = "__compiled_op";

    private OpRuntimeDispatcher() {}

    public static RuntimeHandle activate(CompiledArray compiled, ServerLevel level) {
        return activate(compiled, level, null);
    }

    /** Activates a runtime with its array frame available before registration. */
    public static RuntimeHandle activate(
            CompiledArray compiled, ServerLevel level, ArrayObject array) {
        return activate(new RuntimeModel(compiled.root(), compiled.wirelessDependencyKeys(),
                        compiled.manaElements()),
                level, array, compiled.rootCircleGlyph());
    }

    public static RuntimeHandle activate(
            RuntimeModel model, ServerLevel level, ArrayObject array) {
        return activate(model, level, array,
                array == null || model.root() == null
                        ? model.root() == null ? null : model.root().boundary()
                        : array.rootCircleGlyph());
    }

    private static RuntimeHandle activate(
            RuntimeModel model, ServerLevel level, ArrayObject array,
            PositionedGlyph rootGlyph) {
        if (!(model.root() instanceof PersistentOp persistent)) {
            return new RuntimeHandle(Map.of());
        }

        OpRuntimeContext context = new OpRuntimeContext(level, model.root());
        if (array != null) {
            context = context.withArray(array, rootGlyph);
        }
        RuntimeHandle handle = persistent.activate(context);
        Map<String, Object> data = new HashMap<>(handle.scratchData());
        data.put(RUNTIME_KEY, model.root().id().toString());
        data.put(COMPILED_OP_KEY, model.root());
        // The activated effect keeps its own persisted copy of the array's
        // solved mana elements; consumption is not implemented yet.
        data.put(ManaElements.SCRATCH_KEY, model.manaElements());
        return new RuntimeHandle(Map.copyOf(data));
    }

    public static void deactivate(ServerLevel level, ArrayObject array) {
        RuntimeModel model = array.scratchData().get(COMPILED_OP_KEY) instanceof CompiledOp cached
                ? new RuntimeModel(cached, java.util.Set.of())
                : rebuildForTeardown(level, array);
        if (model != null) deactivate(level, array, model);
    }

    public static void deactivate(ServerLevel level, ArrayObject array,
                                  RuntimeModel model) {
        if (!(model.root() instanceof PersistentOp persistent)) return;
        OpRuntimeContext context = new OpRuntimeContext(level, model.root())
                .withArray(array, array.rootCircleGlyph());
        persistent.deactivate(context, array.scratchData());
    }

    private static RuntimeModel rebuildForTeardown(ServerLevel level, ArrayObject array) {
        CompileResult<RuntimeModel> result = ArrayCompilePipeline.rebuildForTeardown(
                array, level.getData(ModAttachments.ARRAY_MANAGER));
        if (!(result instanceof CompileResult.Success<RuntimeModel> success)) return null;
        RuntimeModel model = success.value();
        Object storedRuntime = array.scratchData().get(RUNTIME_KEY);
        if (storedRuntime instanceof String runtimeId
                && !model.root().id().toString().equals(runtimeId)) return null;
        return model;
    }
}
