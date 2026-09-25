package com.astune.gyromancy.array.compile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.array.MagicArrayManager;

/** Owns the fixed static-then-local compilation order. */
public final class ArrayCompilePipeline {
    private final Collection<? extends OpDefinition> definitions;

    public ArrayCompilePipeline(Collection<? extends OpDefinition> definitions) {
        this.definitions = List.copyOf(definitions);
    }

    public CompileResult<RuntimeModel> compile(
            GroupNode ast, StaticResolveContext staticContext) {
        return compile(ast, staticContext, true);
    }

    private CompileResult<RuntimeModel> compile(
            GroupNode ast, StaticResolveContext staticContext, boolean verifyManaCost) {
        CompileResult<Result> result = compileDetailed(ast, staticContext, verifyManaCost);
        if (result instanceof CompileResult.Failure<Result> failure) {
            return new CompileResult.Failure<>(failure.diagnostics());
        }
        return new CompileResult.Success<>(
                ((CompileResult.Success<Result>) result).value().runtimeModel());
    }

    public CompileResult<RuntimeModel> compile(GroupNode ast) {
        return compile(ast, new StaticResolveContext(
                null, null, List.copyOf(definitions), ast.boundary(), List.of()));
    }

    public CompileResult<Result> compileDetailed(
            GroupNode ast, StaticResolveContext staticContext) {
        return compileDetailed(ast, staticContext, true);
    }

    public CompileResult<Result> compileDetailed(
            GroupNode ast, StaticResolveContext staticContext, boolean verifyManaCost) {
        CompileResult<CompiledArray> staticResult =
                new StaticCompiler(definitions).compile(ast, staticContext);
        if (staticResult instanceof CompileResult.Failure<CompiledArray> failure) {
            return new CompileResult.Failure<>(failure.diagnostics());
        }
        CompiledArray staticModel = ((CompileResult.Success<CompiledArray>) staticResult).value();
        CompileResult<RuntimeModel> runtimeResult = new LocalCompiler().compile(staticModel);
        if (runtimeResult instanceof CompileResult.Failure<RuntimeModel> failure) {
            return new CompileResult.Failure<>(failure.diagnostics());
        }
        RuntimeModel runtimeModel = ((CompileResult.Success<RuntimeModel>) runtimeResult).value();
        if (verifyManaCost) {
            List<CompileDiagnostic> shortfalls =
                    ArrayManaCost.check(runtimeModel.root(), staticModel.manaElements());
            if (!shortfalls.isEmpty()) {
                List<CompileDiagnostic> diagnostics = new ArrayList<>();
                diagnostics.add(new CompileDiagnostic(CompileDiagnostic.RUNTIME_ERROR,
                        "The array does not hold enough elements for its cost"));
                diagnostics.addAll(shortfalls);
                return new CompileResult.Failure<>(diagnostics);
            }
        }
        return new CompileResult.Success<>(new Result(staticModel, runtimeModel));
    }

    public record Result(CompiledArray staticModel, RuntimeModel runtimeModel) {}

    /** Rebuilds only enough of an array to deliver a safe teardown after reload. */
    public static CompileResult<RuntimeModel> rebuildForTeardown(
            ArrayObject array, MagicArrayManager sourceManager) {
        MagicArrayManager isolated = new MagicArrayManager(sourceManager.opDefinitions());
        for (var glyph : array.allBoundGlyphs()) isolated.restoreGlyph(glyph);
        var root = isolated.getGlyph(array.rootCircleGlyph().glyphUuid());
        if (root == null) {
            return new CompileResult.Failure<>(List.of(new CompileDiagnostic(
                    "missing_teardown_root", "Persisted array root is unavailable")));
        }

        GroupNode ast = ArrayAstBuilder.build(root, isolated);
        StaticResolveContext context = new StaticResolveContext(
                null, isolated, isolated.opDefinitions(), root, List.of());
        // Teardown must never be blocked by the mana cost gate.
        return new ArrayCompilePipeline(isolated.opDefinitions())
                .compile(ast, context, false);
    }

}
