package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.compile.operator.CompiledOp;

import java.util.List;

public sealed interface LocalCompileResult
        permits LocalCompileResult.Success, LocalCompileResult.Failure {
    record Success(CompiledOp operator, LocalForwardContext forwarded)
            implements LocalCompileResult {}

    record Failure(List<CompileDiagnostic> diagnostics) implements LocalCompileResult {
        public Failure {
            diagnostics = List.copyOf(diagnostics);
        }
    }

    static Success success(CompiledOp operator) {
        return new Success(operator, null);
    }

    static Failure failure(String code, String message) {
        return new Failure(List.of(new CompileDiagnostic(code, message)));
    }
}
