package com.astune.gyromancy.array.compile;

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
        CompileResult<Result> result = compileDetailed(ast, staticContext);
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
        return new CompileResult.Success<>(new Result(
                staticModel,
                ((CompileResult.Success<RuntimeModel>) runtimeResult).value()));
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
        return new ArrayCompilePipeline(isolated.opDefinitions()).compile(ast, context);
    }

}
