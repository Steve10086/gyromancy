package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.VectorCompiler;
import com.astune.gyromancy.compile.vector.StaticVectorDefinition;
import com.astune.gyromancy.compile.vector.VectorOp;

import java.util.ArrayList;
import java.util.List;

/** Compile-side support selected by Momentum's operator definition. */
final class MomentumInputResolver {
    private static final double DRAIN_ROTATION_SCALE = 18.0;

    private final VectorCompiler vectorCompiler;

    MomentumInputResolver(VectorCompiler vectorCompiler) {
        this.vectorCompiler = vectorCompiler;
    }

    ResolvedInputs resolve(PositionedGlyph boundary, List<OpInput> inputs) {
        return resolve(boundary, inputs, OpResolveContext.forVector(boundary));
    }

    ResolvedInputs resolve(PositionedGlyph boundary, List<OpInput> inputs,
                           OpResolveContext context) {
        List<MomentumOp.VectorInput> velocityInputs = new ArrayList<>();
        List<MomentumOp.AccelerationInput> accelerationInputs = new ArrayList<>();
        boolean dynamic = false;

        for (OpInput input : inputs) {
            if (input instanceof OpInput.Rune rune) {
                if ("loop".equals(rune.symbolName())) {
                    dynamic = true;
                    continue;
                }
                addDirectVector(velocityInputs, boundary, rune, inputs);
                continue;
            }

            // A dynamically replaced child (for example a resolved Wireless
            // source) carries its authored source group. Interpret that source
            // as a vector first, matching the historical VECTOR use-site path.
            // A nested MomentumOp group also carries a source group, but its
            // direct `motion` rune never matches the vector definitions, so it
            // still falls through to the acceleration branch below.
            if (input instanceof OpInput.Op op && op.sourceGroup() != null
                    && addCompiledVector(velocityInputs, vectorCompiler.compile(input, context))) {
                continue;
            }

            if (input instanceof OpInput.Op op && op.operator() instanceof MomentumOp momentum) {
                addMomentumAcceleration(accelerationInputs, momentum, context);
                continue;
            }

            addVectorGroup(velocityInputs, input, context);
        }

        ResolvedInputs resolved = new ResolvedInputs(List.copyOf(velocityInputs),
                List.copyOf(accelerationInputs), dynamic);
        com.astune.gyromancy.Gyromancy.LOGGER.debug(
                "[Momentum] resolve boundary={} dynamic={} velocity={} acceleration={}",
                boundary == null ? "none" : boundary.glyphId(), dynamic,
                resolved.velocityInputs().size(), resolved.accelerationInputs().size());
        return resolved;
    }

    double drainRotationSpeed(List<OpInput> inputs) {
        return inputs.stream()
                .filter(OpInput.Rune.class::isInstance)
                .map(OpInput.Rune.class::cast)
                .filter(rune -> "drain".equals(rune.symbolName()))
                .findFirst()
                .map(rune -> rune.glyph().length() * DRAIN_ROTATION_SCALE)
                .orElse(0.0);
    }

    private void addDirectVector(List<MomentumOp.VectorInput> result,
                                 PositionedGlyph boundary, OpInput.Rune rune,
                                 List<OpInput> inputs) {
        StaticVectorDefinition.vectorFor(rune.symbolName(), boundary, rune.glyph())
                .ifPresent(vector -> result.add(new MomentumOp.VectorInput(
                        vector, motionMode(rune.symbolName()))));
    }

    private void addVectorGroup(List<MomentumOp.VectorInput> result, OpInput input,
                                OpResolveContext context) {
        if (input instanceof OpInput.Op op && op.operator() instanceof MomentumOp) return;

        if (input instanceof OpInput.RawGroup
                || input instanceof OpInput.Op op && op.sourceGroup() != null) {
            CompileResult<CompiledOp> compiled = vectorCompiler.compile(input, context);
            if (addCompiledVector(result, compiled)) return;
            com.astune.gyromancy.Gyromancy.LOGGER.debug(
                    "[Momentum] vector group dropped input={} result={}",
                    describe(input), compiled instanceof CompileResult.Failure<CompiledOp> failure
                            ? failure.diagnostics() : compiled);
        }

        if (input instanceof OpInput.Op op && op.operator() instanceof VectorOp vector) {
            result.add(new MomentumOp.VectorInput(vector, MomentumOp.MotionMode.DIRECT));
        }
    }

    private static String describe(OpInput input) {
        return switch (input) {
            case OpInput.RawGroup raw -> "RawGroup(" + raw.boundary().symbolId().getPath()
                    + "#" + raw.boundary().glyphId() + ")";
            case OpInput.Op op -> "Op(" + op.operator().getClass().getSimpleName() + ")";
            case OpInput.Rune rune -> "Rune(" + rune.symbolName() + ")";
        };
    }

    private static void addMomentumAcceleration(
            List<MomentumOp.AccelerationInput> result, MomentumOp momentum,
            OpResolveContext context) {
        ResolvedInputs resolved = new ResolvedInputs(momentum.velocityInputs(),
                momentum.accelerationInputs(), momentum.dynamic());
        MomentumOp.UpdateMode updateMode = resolved.dynamic()
                ? MomentumOp.UpdateMode.DYNAMIC
                : MomentumOp.UpdateMode.SNAPSHOT;
        for (MomentumOp.VectorInput input : resolved.velocityInputs()) {
            result.add(new MomentumOp.AccelerationInput(
                    input.vector(), input.motionMode(), updateMode));
        }
        // Preserve acceleration authored by deeper nested MomentumOps when a
        // MomentumOp is itself consumed as a vector source.
        result.addAll(resolved.accelerationInputs());
    }

    private static boolean addCompiledVector(
            List<MomentumOp.VectorInput> result, CompileResult<CompiledOp> compiled) {
        if (compiled instanceof CompileResult.Success<CompiledOp> success
                && success.value() instanceof VectorOp vector) {
            result.add(new MomentumOp.VectorInput(vector, MomentumOp.MotionMode.DIRECT));
            return true;
        }
        return false;
    }

    private static MomentumOp.MotionMode motionMode(String runeName) {
        return "arrow".equals(runeName)
                ? MomentumOp.MotionMode.TANGENTIAL
                : MomentumOp.MotionMode.DIRECT;
    }

    record ResolvedInputs(List<MomentumOp.VectorInput> velocityInputs,
                          List<MomentumOp.AccelerationInput> accelerationInputs,
                          boolean dynamic) {}
}
