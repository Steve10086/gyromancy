package com.astune.gyromancy.array.runtime;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.array.MagicArrayManager;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.ArrayAstBuilder;
import com.astune.gyromancy.array.compile.ArrayCompileDebug;
import com.astune.gyromancy.array.compile.ArrayNodeCompiler;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.CompiledArray;
import com.astune.gyromancy.array.compile.GroupNode;
import com.astune.gyromancy.entity.ball.MagicBallEntity;
import com.astune.gyromancy.registry.ModAttachments;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ArrayEffectLifecycle {
    private ArrayEffectLifecycle() {}

    public static Optional<ArrayObject> activateOrReplace(ServerLevel level, PositionedGlyph circleGlyph) {
        MagicArrayManager mgr = level.getData(ModAttachments.ARRAY_MANAGER);
        GroupNode ast = ArrayAstBuilder.build(circleGlyph, mgr);
        ArrayCompileDebug.printAst(level, ast);

        CompileResult<CompiledArray> result = ArrayNodeCompiler.compile(ast, mgr.opDefinitions());
        if (!(result instanceof CompileResult.Success<CompiledArray> success)) {
            if (result instanceof CompileResult.Failure<CompiledArray> failure) {
                Gyromancy.LOGGER.debug("[MagicArrayDetector] Compile failed for glyph #{}: {}",
                        circleGlyph.glyphId(), failure.diagnostics());
                ArrayCompileDebug.printFailure(level, failure);
            }
            return Optional.empty();
        }

        ArrayObject existing = mgr.getArrayForGlyph(circleGlyph.glyphUuid());
        if (existing != null) deactivate(level, existing);

        CompiledArray compiled = success.value();
        RuntimeHandle handle = OpRuntimeDispatcher.activate(compiled, level);
        Map<String, Object> scratchData = new HashMap<>(handle.scratchData());
        scratchData.put("__array_color", compiled.color());

        ArrayObject arr = new ArrayObject(UUID.randomUUID(), compiled.rootCircleGlyph(),
                compiled.boundGlyphs(), Map.copyOf(scratchData));
        mgr.registerArrayObj(arr);
        bindPersistentEntities(level, arr.arrayId(), arr.scratchData());
        Gyromancy.LOGGER.info("[MagicArrayDetector] Array activated: root={}, bound={}",
                circleGlyph.symbolId(), compiled.boundGlyphs().size());
        return Optional.of(arr);
    }

    public static boolean deactivateForGlyph(ServerLevel level, PositionedGlyph glyph) {
        MagicArrayManager mgr = level.getData(ModAttachments.ARRAY_MANAGER);
        ArrayObject arr = mgr.getArrayForGlyph(glyph.glyphUuid());
        if (arr == null) return false;
        deactivate(level, arr);
        return true;
    }

    public static void deactivate(ServerLevel level, ArrayObject array) {
        OpRuntimeDispatcher.deactivate(level, array);
        level.getData(ModAttachments.ARRAY_MANAGER).unregisterArrayObj(array.arrayId());
        Gyromancy.LOGGER.info("[MagicArrayDetector] Array deactivated: root={}",
                array.rootCircleGlyph().symbolId());
    }

    private static void bindPersistentEntities(ServerLevel level, UUID arrayId, Map<String, Object> scratchData) {
        for (Map.Entry<String, Object> entry : scratchData.entrySet()) {
            if (!(entry.getValue() instanceof ArrayObject.EntityRef ref)) continue;
            if (ref.resolve(level) instanceof MagicBallEntity ball) {
                ball.bindToArray(arrayId);
            }
        }
    }
}
