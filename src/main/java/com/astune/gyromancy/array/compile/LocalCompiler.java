package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.compile.operator.CompiledOp;

import java.util.IdentityHashMap;
import java.util.Map;

/** Stage-2 pass which recursively materializes every compiled Op. */
public final class LocalCompiler {
    private final Map<CompiledOp, CompiledOp> cache = new IdentityHashMap<>();

    public CompileResult<RuntimeModel> compile(CompiledArray staticModel) {
        CompileResult<CompiledOp> root = materialize(staticModel.root());
        if (root instanceof CompileResult.Failure<CompiledOp> failure) {
            return new CompileResult.Failure<>(failure.diagnostics());
        }
        return new CompileResult.Success<>(new RuntimeModel(
                ((CompileResult.Success<CompiledOp>) root).value(),
                staticModel.wirelessDependencyKeys(),
                staticModel.manaElements()));
    }

    CompileResult<CompiledOp> materialize(CompiledOp operator) {
        CompiledOp cached = cache.get(operator);
        if (cached != null) return new CompileResult.Success<>(cached);

        LocalCompileContext context = new LocalCompileContext(this);
        LocalCompileResult localResult = operator.localCompile(context);
        if (!context.failures().isEmpty()) {
            return new CompileResult.Failure<>(context.failures());
        }
        if (localResult instanceof LocalCompileResult.Failure failure) {
            return new CompileResult.Failure<>(failure.diagnostics());
        }

        CompiledOp result = ((LocalCompileResult.Success) localResult).operator();
        cache.put(operator, result);
        return new CompileResult.Success<>(result);
    }

    OpInput materialize(OpInput input) {
        if (!(input instanceof OpInput.Op child)) return input;
        CompileResult<CompiledOp> result = materialize(child.operator());
        if (result instanceof CompileResult.Failure<CompiledOp> failure) {
            return new OpInput.RawGroup(child.sourceGroup(), failure.diagnostics());
        }
        return new OpInput.Op(((CompileResult.Success<CompiledOp>) result).value(),
                child.sourceGroup());
    }
}
