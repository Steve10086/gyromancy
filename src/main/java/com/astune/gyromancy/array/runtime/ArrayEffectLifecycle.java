package com.astune.gyromancy.array.runtime;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.array.MagicArrayManager;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.array.compile.ArrayAstBuilder;
import com.astune.gyromancy.array.compile.ArrayCompileDebug;
import com.astune.gyromancy.array.compile.ArrayCompileFeedback;
import com.astune.gyromancy.array.compile.ArrayCompilePipeline;
import com.astune.gyromancy.array.compile.CompileDiagnostic;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.CompiledArray;
import com.astune.gyromancy.array.compile.GroupNode;
import com.astune.gyromancy.array.compile.StaticResolveContext;
import com.astune.gyromancy.array.compile.RuntimeModel;
import com.astune.gyromancy.compile.operator.PersistentOp;
import com.astune.gyromancy.array.runtime.emit.EmitResult;
import com.astune.gyromancy.array.runtime.emit.EmittedObject;
import com.astune.gyromancy.array.runtime.wireless.WirelessDependencyCoordinator;
import com.astune.gyromancy.entity.ball.MagicBallEntity;
import com.astune.gyromancy.entity.field.MagicFieldEntity;
import com.astune.gyromancy.registry.ModAttachments;
import com.astune.gyromancy.symbol.GlyphStrokeValidator;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.ArrayList;
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
        return compileNew(level, circleGlyph, isValidStroke, null);
    }

    private static Optional<ArrayObject> compileNew(
            ServerLevel level,
            PositionedGlyph circleGlyph,
            Predicate<PositionedGlyph> isValidStroke,
            @Nullable List<ArrayCompileFeedback.Issue> issues
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

        CompileResult<ArrayCompilePipeline.Result> result = new ArrayCompilePipeline(
                mgr.opDefinitions()).compileDetailed(ast,
                new StaticResolveContext(level, mgr, mgr.opDefinitions(),
                        circleGlyph, List.of()));
        if (!(result instanceof CompileResult.Success<ArrayCompilePipeline.Result> success)) {
            if (result instanceof CompileResult.Failure<ArrayCompilePipeline.Result> failure) {
                reportIssue(level, issues, circleGlyph, failure.diagnostics(), false);
                ArrayCompileDebug.printFailure(level, failure);
            }
            mgr.releaseCircleCompilation(circleGlyph.glyphUuid());
            registerMissingDependencies(level, circleGlyph, failureOrEmpty(result));
            return Optional.empty();
        }

        CompiledArray compiled = success.value().staticModel();
        RuntimeModel runtime = success.value().runtimeModel();
        ArrayCompileDebug.printRuntime(level, runtime);
        ArrayCompileDebug.logRuntime(runtime);
        if (!(runtime.root() instanceof PersistentOp)) {
            reportIssue(level, issues, circleGlyph, List.of(), true);
            mgr.releaseCircleCompilation(circleGlyph.glyphUuid());
            return Optional.empty();
        }

        WirelessDependencyCoordinator.cancelPending(level, circleGlyph.glyphUuid());

        return activateCompiled(level, mgr, compiled, runtime);
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
        MagicArrayManager mgr = level.getData(ModAttachments.ARRAY_MANAGER);
        List<ArrayCompileFeedback.Issue> issues = new ArrayList<>();
        glyphs.stream()
                .filter(glyph -> glyph.role() == SymbolRole.OUTER_CIRCLE)
                .sorted(Comparator.comparingDouble((PositionedGlyph glyph) -> glyph.bounds().area())
                        .thenComparingInt(PositionedGlyph::glyphId))
                .forEach(circle -> {
                    Predicate<PositionedGlyph> filter = isValidStroke != null
                            ? isValidStroke
                            : glyph -> GlyphStrokeValidator.isValidForCollection(glyph, mgr, level);
                    compileNew(level, circle, filter, issues);
                });
        ArrayCompileFeedback.reportAll(level, issues);
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

    /** Rebuilds live runtime models for arrays restored from level persistence. */
    public static int rebuildAll(ServerLevel level) {
        MagicArrayManager manager = level.getData(ModAttachments.ARRAY_MANAGER);
        int rebuilt = 0;
        for (ArrayObject array : List.copyOf(manager.getAllArrayObjs())) {
            if (RuntimeModelRegistry.get(level, array.arrayId()) != null) continue;
            PositionedGlyph root = manager.getGlyph(array.rootCircleGlyph().glyphUuid());
            if (root == null || root.role() != SymbolRole.OUTER_CIRCLE) continue;

            GroupNode ast = ArrayAstBuilder.build(root, manager,
                    glyph -> GlyphStrokeValidator.isValidForCollection(glyph, manager, level));
            CompileResult<ArrayCompilePipeline.Result> result = new ArrayCompilePipeline(
                    manager.opDefinitions()).compileDetailed(ast,
                    new StaticResolveContext(level, manager, manager.opDefinitions(),
                            root, List.of()));
            if (!(result instanceof CompileResult.Success<ArrayCompilePipeline.Result> success)
                    || !(success.value().runtimeModel().root() instanceof PersistentOp)) {
                if (result instanceof CompileResult.Failure<ArrayCompilePipeline.Result> failure) {
                    ArrayCompileFeedback.reportFailure(level, root, failure.diagnostics());
                    registerMissingDependencies(level, root, failure);
                } else {
                    ArrayCompileFeedback.reportNotRunnable(level, root);
                }
                continue;
            }

            CompiledArray compiled = success.value().staticModel();
            RuntimeModel runtime = success.value().runtimeModel();
            RuntimeModelRegistry.put(level, array.arrayId(), runtime);
            for (String key : compiled.wirelessDependencyKeys()) {
                level.getData(ModAttachments.WIRELESS_REGISTRY)
                        .subscribe(key, array.arrayId());
            }
            RuntimeHandle handle = OpRuntimeDispatcher.activate(runtime, level, array);
            Map<String, Object> scratch = new HashMap<>(array.scratchData());
            scratch.putAll(handle.scratchData());
            manager.setArrayScratchData(array.arrayId(), scratch);
            bindPersistentEntities(level, array.arrayId(), scratch);
            rebuilt++;
        }
        return rebuilt;
    }

    public static void deactivate(ServerLevel level, ArrayObject array) {
        RuntimeModel model = RuntimeModelRegistry.get(level, array.arrayId());
        if (model == null) OpRuntimeDispatcher.deactivate(level, array);
        else OpRuntimeDispatcher.deactivate(level, array, model);
        RuntimeModelRegistry.remove(level, array.arrayId());
        discardNonProjectileEmittedEntities(level, array.arrayId());
        level.getData(ModAttachments.WIRELESS_REGISTRY).unsubscribe(array.arrayId());
        WirelessDependencyCoordinator.cancelPending(level, array.rootCircleGlyph().glyphUuid());
        level.getData(ModAttachments.ARRAY_MANAGER).unregisterArrayObj(array.arrayId());
        Gyromancy.LOGGER.info("[MagicArrayDetector] Array deactivated: root={}",
                array.rootCircleGlyph().symbolId());
    }

    private static Optional<ArrayObject> activateCompiled(
            ServerLevel level, MagicArrayManager manager, CompiledArray compiled,
            RuntimeModel runtimeModel) {
        UUID arrayId = UUID.randomUUID();
        long compilationEffectEndTick =
                level.getGameTime() + ArrayObject.COMPILATION_EFFECT_TICKS;
        ArrayObject activationArray = new ArrayObject(
                arrayId,
                compiled.rootCircleGlyph(),
                compiled.boundGlyphs(),
                compilationEffectEndTick,
                Map.of("__array_color", compiled.color()),
                compiled.manaElements());
        RuntimeHandle handle = OpRuntimeDispatcher.activate(
                runtimeModel, level, activationArray);
        if (OpRuntimeFailure.consumePendingTermination(activationArray)) {
            EmitResult.discardEmittedEntities(level, handle.scratchData());
            manager.releaseCircleCompilation(compiled.rootCircleGlyph().glyphUuid());
            return Optional.empty();
        }
        Map<String, Object> scratchData = new HashMap<>(handle.scratchData());
        scratchData.put("__array_color", compiled.color());

        ArrayObject array = new ArrayObject(
                arrayId,
                compiled.rootCircleGlyph(),
                compiled.boundGlyphs(),
                compilationEffectEndTick,
                Map.copyOf(scratchData),
                compiled.manaElements());
        manager.registerArrayObj(array);
        RuntimeModelRegistry.put(level, arrayId, runtimeModel);
        for (String key : compiled.wirelessDependencyKeys()) {
            level.getData(ModAttachments.WIRELESS_REGISTRY).subscribe(key, arrayId);
        }
        bindPersistentEntities(level, array.arrayId(), array.scratchData());
        Gyromancy.LOGGER.info("[MagicArrayDetector] Array activated: root={}, bound={}",
                compiled.rootCircleGlyph().symbolId(), compiled.boundGlyphs().size());
        return Optional.of(array);
    }

    private static void registerMissingDependencies(
            ServerLevel level, PositionedGlyph root, CompileResult.Failure<?> failure) {
        List<String> keys = failure.diagnostics().stream()
                .filter(diagnostic -> "missing_wireless_source".equals(diagnostic.code()))
                .map(CompileDiagnostic::message)
                .distinct()
                .toList();
        if (!keys.isEmpty()) {
            WirelessDependencyCoordinator.registerPending(level, root.glyphUuid(), keys);
        }
    }

    private static CompileResult.Failure<?> failureOrEmpty(CompileResult<?> result) {
        return result instanceof CompileResult.Failure<?> failure
                ? failure : new CompileResult.Failure<>(List.of());
    }

    private static void reportIssue(
            ServerLevel level,
            @Nullable List<ArrayCompileFeedback.Issue> issues,
            PositionedGlyph root,
            List<CompileDiagnostic> diagnostics,
            boolean notRunnable) {
        if (issues != null) {
            issues.add(new ArrayCompileFeedback.Issue(root, diagnostics, notRunnable));
        } else if (notRunnable) {
            ArrayCompileFeedback.reportNotRunnable(level, root);
        } else {
            ArrayCompileFeedback.reportFailure(level, root, diagnostics);
        }
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

    /**
     * Projectile balls enter their own deferred-discard lifecycle during the
     * root operator's teardown. Other emitted entities still stop immediately.
     */
    private static void discardNonProjectileEmittedEntities(ServerLevel level, UUID arrayId) {
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
                        && !(entity instanceof MagicBallEntity)
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
