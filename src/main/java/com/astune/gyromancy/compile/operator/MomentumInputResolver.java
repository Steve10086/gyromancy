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

            if (input instanceof OpInput.Op op && op.operator() instanceof MomentumOp momentum) {
                addMomentumAcceleration(accelerationInputs, momentum);
                continue;
            }

            addVectorGroup(velocityInputs, input, context);
        }

        return new ResolvedInputs(List.copyOf(velocityInputs),
                List.copyOf(accelerationInputs), dynamic);
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
            if (addCompiledVector(result, vectorCompiler.compile(input, context))) return;
        }

        if (input instanceof OpInput.Op op && op.operator() instanceof VectorOp vector) {
            result.add(new MomentumOp.VectorInput(vector, MomentumOp.MotionMode.DIRECT));
        }
    }

    private static void addMomentumAcceleration(
            List<MomentumOp.AccelerationInput> result, MomentumOp momentum) {
        MomentumOp.UpdateMode updateMode = momentum.dynamic()
                ? MomentumOp.UpdateMode.DYNAMIC
                : MomentumOp.UpdateMode.SNAPSHOT;
        for (MomentumOp.VectorInput input : momentum.velocityInputs()) {
            result.add(new MomentumOp.AccelerationInput(
                    input.vector(), input.motionMode(), updateMode));
        }
        // Preserve acceleration authored by deeper nested MomentumOps when a
        // MomentumOp is itself consumed as a vector source.
        result.addAll(momentum.accelerationInputs());
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
