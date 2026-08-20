package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.compile.operator.CompiledOp;

import java.util.Collection;
import java.util.List;

public final class ArrayNodeCompiler {
    private ArrayNodeCompiler() {}

    //TODO: cleanup after acceptance: remove old CompiledArrayNode/EffectNode/ArrayScript classes and this migration note.
    public static CompileResult<CompiledArray> compile(GroupNode ast) {
        return compile(ast, OpDefinitionRegistry.definitions());
    }

    public static CompileResult<CompiledArray> compile(GroupNode ast, Collection<? extends OpDefinition> effects) {
        CompileResult<CompiledOp> root = new GlobalCompiler(effects).compile(ast);
        if (root instanceof CompileResult.Failure<CompiledOp> failure) {
            return new CompileResult.Failure<>(failure.diagnostics());
        }

        CompiledOp op = ((CompileResult.Success<CompiledOp>) root).value();
        return new CompileResult.Success<>(new CompiledArray(
                op,
                ast.boundary(),
                GroupCompiler.boundGlyphs(op),
                op.color()));
    }
}
