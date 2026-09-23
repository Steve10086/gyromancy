package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.compile.operator.CompiledOp;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

/** Context exposed to an Op during its optional local compilation step. */
public final class LocalCompileContext {
    private final LocalCompiler compiler;
    private final List<CompileDiagnostic> failures = new ArrayList<>();

    public LocalCompileContext(LocalCompiler compiler) {
        this.compiler = Objects.requireNonNull(compiler, "compiler");
    }

    public OpInput materialize(OpInput input) {
        if (!(input instanceof OpInput.Op child)) return input;
        CompileResult<CompiledOp> result = compiler.materialize(child.operator());
        if (result instanceof CompileResult.Failure<CompiledOp> failure) {
            recordFailure(failure.diagnostics());
            return new OpInput.RawGroup(child.sourceGroup(), failure.diagnostics());
        }
        return new OpInput.Op(((CompileResult.Success<CompiledOp>) result).value(),
                child.sourceGroup());
    }

    void recordFailure(List<CompileDiagnostic> diagnostics) {
        failures.addAll(diagnostics);
    }

    List<CompileDiagnostic> failures() {
        return List.copyOf(failures);
    }
}
