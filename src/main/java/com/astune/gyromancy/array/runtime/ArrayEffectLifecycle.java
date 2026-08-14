package com.astune.gyromancy.array.runtime;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.array.MagicArrayManager;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.compile.ArrayAstBuilder;
import com.astune.gyromancy.array.compile.ArrayCompileDebug;
import com.astune.gyromancy.array.compile.ArrayNodeCompiler;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.CompiledArray;
import com.astune.gyromancy.array.compile.GroupNode;
import com.astune.gyromancy.compile.operator.PersistentOp;
import com.astune.gyromancy.array.runtime.emit.EmitResult;
import com.astune.gyromancy.array.runtime.emit.EmittedObject;
import com.astune.gyromancy.entity.ball.MagicBallEntity;
import com.astune.gyromancy.entity.field.MagicFieldEntity;
import com.astune.gyromancy.registry.ModAttachments;
import com.astune.gyromancy.symbol.GlyphStrokeValidator;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
        // A nested circle is compiled as a child of its parent AST.  Never
        // start (or restart) a second, independent runtime for that root; if
        // one was created before the parent relation appeared, tear it down
        // at the same boundary as well.
        if (mgr.parentCircle(circleGlyph) != null) {
            deactivateForRootGlyph(level, circleGlyph.glyphUuid());
            return Optional.empty();
        }
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
     * Compiles all supplied outer circles in geometry order. All glyphs must
     * already be registered before this method is called so nested-circle
     * ownership is complete when the AST is built.
     */
    public static void compileCirclesSmallestFirst(
            ServerLevel level, Collection<PositionedGlyph> glyphs) {
        compileCirclesSmallestFirst(level, glyphs, null);
    }

    /** Same as {@link #compileCirclesSmallestFirst(ServerLevel, Collection)} with a stroke filter. */
    public static void compileCirclesSmallestFirst(
            ServerLevel level,
            Collection<PositionedGlyph> glyphs,
            Predicate<PositionedGlyph> isValidStroke) {
        glyphs.stream()
                .filter(glyph -> glyph.role() == SymbolRole.OUTER_CIRCLE)
                .sorted(Comparator.comparingDouble((PositionedGlyph glyph) -> glyph.bounds().area())
                        .thenComparingInt(PositionedGlyph::glyphId))
                .forEach(circle -> {
                    if (isValidStroke == null) compileNew(level, circle);
                    else compileNew(level, circle, isValidStroke);
                });
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

    /**
     * Stops runtimes whose root circle has just become a child of another
     * circle.  Hierarchy ownership is rebuilt by {@link MagicArrayManager}
     * when glyphs are registered, so this check deliberately runs after that
     * rebuild and snapshots the roots before deactivating any arrays.
     *
     * <p>The nested circle remains part of its parent's AST; only the stale
     * independent runtime is removed.</p>
     */
    public static int deactivateParentedRootArrays(ServerLevel level) {
        MagicArrayManager mgr = level.getData(ModAttachments.ARRAY_MANAGER);
        List<UUID> parentedRoots = mgr.getAllArrayObjs().stream()
                .map(ArrayObject::rootCircleGlyph)
                .filter(root -> mgr.parentCircle(root) != null)
                .map(PositionedGlyph::glyphUuid)
                .distinct()
                .toList();
        for (UUID rootGlyphId : parentedRoots) {
            deactivateForRootGlyph(level, rootGlyphId);
        }
        return parentedRoots.size();
    }

    public static void deactivate(ServerLevel level, ArrayObject array) {
        OpRuntimeDispatcher.deactivate(level, array);
        discardAllEmittedEntities(level, array.arrayId());
        level.getData(ModAttachments.ARRAY_MANAGER).unregisterArrayObj(array.arrayId());
        Gyromancy.LOGGER.info("[MagicArrayDetector] Array deactivated: root={}",
                array.rootCircleGlyph().symbolId());
    }

    /**
     * Binds entities emitted by a discard handler to the live array. The
     * handler may run while the parent array is being torn down, so this also
     * keeps those entities in the array's normal emission list.
     */
    public static void bindEmittedEntities(ServerLevel level, UUID arrayId, Map<String, Object> scratchData) {
        MagicArrayManager mgr = level.getData(ModAttachments.ARRAY_MANAGER);
        ArrayObject array = mgr.getArrayObj(arrayId);
        if (array == null) return;

        List<EmittedObject> emitted = EmitResult.emissions(scratchData);
        if (!emitted.isEmpty()) {
            List<EmittedObject> all = new java.util.ArrayList<>(EmitResult.emissions(array.scratchData()));
            for (EmittedObject object : emitted) {
                if (!all.contains(object)) all.add(object);
            }
            Map<String, Object> updated = new HashMap<>(array.scratchData());
            updated.put(EmitResult.EMISSIONS_KEY, List.copyOf(all));
            mgr.setArrayScratchData(arrayId, updated);
        }

        for (EmittedObject object : emitted) {
            if (!(object.ref() instanceof ArrayObject.EntityRef ref)) continue;
            bindEntityToArray(level, arrayId, ref);
        }
        for (Map.Entry<String, Object> entry : scratchData.entrySet()) {
            if (!(entry.getValue() instanceof ArrayObject.EntityRef ref)) continue;
            bindEntityToArray(level, arrayId, ref);
        }
    }

    private static void bindEntityToArray(ServerLevel level, UUID arrayId, ArrayObject.EntityRef ref) {
        if (ref.resolve(level) instanceof MagicBallEntity ball) {
            ball.bindToArray(arrayId);
        } else if (ref.resolve(level) instanceof MagicFieldEntity field) {
            field.bindToArray(arrayId);
        }
    }

    private static void discardAllEmittedEntities(ServerLevel level, UUID arrayId) {
        MagicArrayManager mgr = level.getData(ModAttachments.ARRAY_MANAGER);
        Set<UUID> discarded = new HashSet<>();
        while (true) {
            ArrayObject current = mgr.getArrayObj(arrayId);
            if (current == null) return;
            boolean foundNew = false;
            for (EmittedObject object : EmitResult.emissions(current.scratchData())) {
                if (!(object.ref() instanceof ArrayObject.EntityRef ref)
                        || !discarded.add(ref.uuid())) continue;
                foundNew = true;
                if (ref.resolve(level) instanceof net.minecraft.world.entity.Entity entity
                        && entity.isAlive()) entity.discard();
            }
            if (!foundNew) return;
        }
    }

    private static void bindPersistentEntities(ServerLevel level, UUID arrayId, Map<String, Object> scratchData) {
        Set<UUID> bound = new HashSet<>();
        for (Map.Entry<String, Object> entry : scratchData.entrySet()) {
            if (!(entry.getValue() instanceof ArrayObject.EntityRef ref)) continue;
            if (ref.resolve(level) instanceof MagicBallEntity ball) {
                ball.bindToArray(arrayId);
                bound.add(ball.getUUID());
            } else if (ref.resolve(level) instanceof MagicFieldEntity field) {
                field.bindToArray(arrayId);
                bound.add(field.getUUID());
            }
        }
        for (EmittedObject emitted : EmitResult.emissions(scratchData)) {
            if (!(emitted.ref() instanceof ArrayObject.EntityRef ref)) continue;
            if (ref.resolve(level) instanceof MagicBallEntity ball
                    && bound.add(ball.getUUID())) {
                ball.bindToArray(arrayId);
            } else if (ref.resolve(level) instanceof MagicFieldEntity field
                    && bound.add(field.getUUID())) {
                field.bindToArray(arrayId);
            }
        }
    }
}
