package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.LocalCompileContext;
import com.astune.gyromancy.array.compile.LocalCompileResult;

/** Central identity-preserving entry point for nested operator forwarding. */
public final class OpResolver {
    private OpResolver() {}

    /** Optional second-stage dispatch entry point. */
    public static LocalCompileResult localCompile(
            CompiledOp candidate, LocalCompileContext context) {
        if (candidate instanceof LocalCompilable local) {
            return local.localCompile(context);
        }
        return LocalCompileResult.success(candidate);
    }

}
