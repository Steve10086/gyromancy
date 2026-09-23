package com.astune.gyromancy.array.compile;

import java.util.Collection;
import java.util.List;

/** @deprecated Use {@link StaticCompiler} or {@link ArrayCompilePipeline}. */
@Deprecated(forRemoval = false)
public final class ArrayNodeCompiler {
    private ArrayNodeCompiler() {}

    //TODO: cleanup after acceptance: remove old CompiledArrayNode/EffectNode/ArrayScript classes and this migration note.
    public static CompileResult<CompiledArray> compile(GroupNode ast) {
        return compile(ast, OpDefinitionRegistry.definitions());
    }

    public static CompileResult<CompiledArray> compile(GroupNode ast, Collection<? extends OpDefinition> effects) {
        return new StaticCompiler(effects).compile(ast,
                new StaticResolveContext(null, null, List.copyOf(effects),
                        ast.boundary(), List.of()));
    }
}
