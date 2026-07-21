package com.astune.gyromancy.array.compile;

import java.util.List;

public sealed interface CompileResult<T> permits CompileResult.Success, CompileResult.Failure {
    record Success<T>(T value) implements CompileResult<T> {}
    record Failure<T>(List<CompileDiagnostic> diagnostics) implements CompileResult<T> {}

    default boolean success() {
        return this instanceof Success<T>;
    }
}
