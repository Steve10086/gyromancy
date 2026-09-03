package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.array.compile.OpInput;

/** Central identity-preserving entry point for nested operator forwarding. */
public final class OpResolver {
    private OpResolver() {}

    public static OpResolution resolve(OpInput input, OpResolveContext context) {
        if (input instanceof OpInput.Op nested) {
            OpResolveContext inputContext = context == null
                    ? OpResolveContext.forCompile(nested.operator().boundary())
                    .withSourceGroup(nested.sourceGroup())
                    : context.withSourceGroup(nested.sourceGroup());
            return resolve(nested.operator(), inputContext);
        }
        throw new IllegalArgumentException("Only compiled operator inputs can be resolved");
    }

    public static OpResolution resolve(CompiledOp candidate, OpResolveContext context) {
        if (!(candidate instanceof OpResolvable resolvable)) {
            return OpResolution.unchanged(candidate, context);
        }

        OpResolveContext candidateContext = context == null
                ? OpResolveContext.forCompile(candidate.boundary()).withCandidate(candidate)
                : context.withCandidate(candidate);
        OpResolution resolution = resolvable.resolve(candidateContext);
        if (resolution == null) {
            return OpResolution.unchanged(candidate, candidateContext);
        }
        return resolution;
    }
}
