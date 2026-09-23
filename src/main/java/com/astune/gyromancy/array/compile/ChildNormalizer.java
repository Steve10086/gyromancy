package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.compile.operator.CompiledOp;
import com.astune.gyromancy.compile.operator.OpResolveContext;

@FunctionalInterface
public interface ChildNormalizer {
    CompileResult<NormalizedChild> normalize(
            GroupNode sourceGroup,
            GroupCompileOutcome outcome,
            OpResolveContext context);

    static ChildNormalizer preserving() {
        return (sourceGroup, outcome, ignored) -> switch (outcome) {
            case GroupCompileOutcome.Success success -> new CompileResult.Success<>(
                    new NormalizedChild(new OpInput.Op(success.op(), sourceGroup,
                            success.deferred()), success.dependencyKeys()));
            case GroupCompileOutcome.NoCandidate noCandidate -> new CompileResult.Success<>(
                    new NormalizedChild(new OpInput.RawGroup(sourceGroup, noCandidate.diagnostics()),
                            java.util.Set.of()));
            case GroupCompileOutcome.Failure failure -> new CompileResult.Success<>(
                    new NormalizedChild(new OpInput.RawGroup(sourceGroup, failure.diagnostics()),
                            java.util.Set.of()));
        };
    }
}
