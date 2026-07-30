package com.astune.gyromancy.array.runtime;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.array.MagicArrayManager;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.ArrayAstBuilder;
import com.astune.gyromancy.array.compile.ArrayNodeCompiler;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.CompiledArray;
import com.astune.gyromancy.array.compile.GroupNode;
import com.astune.gyromancy.array.runtime.emit.EmitResult;
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
        if (!(compiled.root() instanceof PersistentOp persistent)) return new RuntimeHandle(Map.of());

        RuntimeHandle handle = persistent.activate(new OpRuntimeContext(level, compiled.root()));
        Map<String, Object> data = new HashMap<>(handle.scratchData());
        data.put(RUNTIME_KEY, compiled.root().id().toString());
        data.put(COMPILED_OP_KEY, compiled.root());
        return new RuntimeHandle(Map.copyOf(data));
    }

    public static void deactivate(ServerLevel level, ArrayObject array) {
        EmitResult.discardEmittedEntities(level, array.scratchData());
        PersistentOp persistent = array.scratchData().get(COMPILED_OP_KEY) instanceof PersistentOp cached
                ? cached
                : recoverPersistentOp(array, level.getData(ModAttachments.ARRAY_MANAGER));
        if (persistent != null) {
            CompiledOp op = persistent;
            persistent.deactivate(new OpRuntimeContext(level, op), array.scratchData());
        }
    }

    /**
     * Compiled operators are runtime-only objects and are intentionally omitted
     * from the array codec. Rebuild just the saved array when deactivating after
     * a restart so its persistent effect still receives the matching teardown.
     */
    static PersistentOp recoverPersistentOp(ArrayObject array, MagicArrayManager sourceManager) {
        MagicArrayManager isolated = new MagicArrayManager(sourceManager.opDefinitions());
        for (PositionedGlyph glyph : array.allBoundGlyphs()) {
            isolated.restoreGlyph(glyph);
        }
        PositionedGlyph root = isolated.getGlyph(array.rootCircleGlyph().glyphUuid());
        if (root == null) return null;

        GroupNode ast = ArrayAstBuilder.build(root, isolated);
        CompileResult<CompiledArray> result =
                ArrayNodeCompiler.compile(ast, isolated.opDefinitions());
        if (!(result instanceof CompileResult.Success<CompiledArray> success)
                || !(success.value().root() instanceof PersistentOp persistent)) {
            return null;
        }

        Object storedRuntime = array.scratchData().get(RUNTIME_KEY);
        if (storedRuntime instanceof String runtimeId
                && !persistent.id().toString().equals(runtimeId)) {
            return null;
        }
        return persistent;
    }
}
