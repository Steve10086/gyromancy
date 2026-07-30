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
import com.astune.gyromancy.compile.operator.PersistentOp;
import com.astune.gyromancy.entity.ball.MagicBallEntity;
import com.astune.gyromancy.registry.ModAttachments;
import com.astune.gyromancy.symbol.GlyphStrokeValidator;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public final class ArrayEffectLifecycle {
    private ArrayEffectLifecycle() {}

    public static Optional<ArrayObject> compileNew(ServerLevel level, PositionedGlyph circleGlyph) {
        MagicArrayManager mgr = level.getData(ModAttachments.ARRAY_MANAGER);
        return compileNew(level, circleGlyph,
                glyph -> GlyphStrokeValidator.isValidForCollection(glyph, mgr, level));
    }

    public static Optional<ArrayObject> compileNew(
            ServerLevel level,
            PositionedGlyph circleGlyph,
            Predicate<PositionedGlyph> isValidStroke
    ) {
        MagicArrayManager mgr = level.getData(ModAttachments.ARRAY_MANAGER);
        if (!mgr.claimCircleCompilation(circleGlyph.glyphUuid())) {
            return Optional.empty();
        }
        GroupNode ast = ArrayAstBuilder.build(circleGlyph, mgr, isValidStroke);
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

        CompiledArray compiled = success.value();
        if (!(compiled.root() instanceof PersistentOp)) return Optional.empty();

        RuntimeHandle handle = OpRuntimeDispatcher.activate(compiled, level);
        Map<String, Object> scratchData = new HashMap<>(handle.scratchData());
        scratchData.put("__array_color", compiled.color());

        ArrayObject arr = new ArrayObject(
                UUID.randomUUID(),
                compiled.rootCircleGlyph(),
                compiled.boundGlyphs(),
                level.getGameTime() + ArrayObject.COMPILATION_EFFECT_TICKS,
                Map.copyOf(scratchData));
        mgr.registerArrayObj(arr);
        bindPersistentEntities(level, arr.arrayId(), arr.scratchData());
        Gyromancy.LOGGER.info("[MagicArrayDetector] Array activated: root={}, bound={}",
                circleGlyph.symbolId(), compiled.boundGlyphs().size());
        return Optional.of(arr);
    }

    /**
     * Kept for source compatibility. Compilation is no longer a replacement
     * operation and will only run once for a circle glyph lifetime.
     */
    @Deprecated(forRemoval = false)
    public static Optional<ArrayObject> activateOrReplace(
            ServerLevel level, PositionedGlyph circleGlyph) {
        return compileNew(level, circleGlyph);
    }

    @Deprecated(forRemoval = false)
    public static Optional<ArrayObject> activateOrReplace(
            ServerLevel level,
            PositionedGlyph circleGlyph,
            Predicate<PositionedGlyph> isValidStroke
    ) {
        return compileNew(level, circleGlyph, isValidStroke);
    }

    public static boolean deactivateForGlyph(ServerLevel level, PositionedGlyph glyph) {
        MagicArrayManager mgr = level.getData(ModAttachments.ARRAY_MANAGER);
        List<ArrayObject> arrays = mgr.getArrayObjsForGlyph(glyph.glyphUuid());
        arrays.forEach(array -> deactivate(level, array));
        return !arrays.isEmpty();
    }

    public static boolean deactivateForRootGlyph(ServerLevel level, UUID rootGlyphId) {
        MagicArrayManager mgr = level.getData(ModAttachments.ARRAY_MANAGER);
        List<ArrayObject> arrays = mgr.getArrayObjsForRoot(rootGlyphId);
        arrays.forEach(array -> deactivate(level, array));
        return !arrays.isEmpty();
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
