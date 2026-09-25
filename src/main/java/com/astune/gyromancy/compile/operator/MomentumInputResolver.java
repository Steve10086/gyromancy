package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.CompileDiagnostic;
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
    private static final String RUNTIME_ERROR_MESSAGE =
            "Momentum cannot resolve a vector source at runtime";

    private final VectorCompiler vectorCompiler;

    MomentumInputResolver(VectorCompiler vectorCompiler) {
        this.vectorCompiler = vectorCompiler;
    }

    CompileResult<ResolvedInputs> resolve(PositionedGlyph boundary, List<OpInput> inputs) {
        return resolve(boundary, inputs, OpResolveContext.forVector(boundary));
    }

    CompileResult<ResolvedInputs> resolve(PositionedGlyph boundary, List<OpInput> inputs,
                                          OpResolveContext context) {
        List<MomentumOp.VectorInput> velocityInputs = new ArrayList<>();
        List<MomentumOp.AccelerationInput> accelerationInputs = new ArrayList<>();
        List<OpInput> treeInputs = new ArrayList<>(inputs.size());
        boolean dynamic = false;

        for (OpInput input : inputs) {
            if (input instanceof OpInput.Rune rune) {
                treeInputs.add(input);
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
            if (input instanceof OpInput.Op op && op.sourceGroup() != null) {
                CompileResult<CompiledOp> compiled = vectorCompiler.compile(input, context);
                if (addCompiledVector(velocityInputs, compiled)) {
                    // The referenced vector replaces its source op in the final tree.
                    treeInputs.add(new OpInput.Op(
                            ((CompileResult.Success<CompiledOp>) compiled).value()));
                    continue;
                }
            }

            if (input instanceof OpInput.Op op && op.operator() instanceof MomentumOp momentum) {
                treeInputs.add(input);
                addMomentumAcceleration(accelerationInputs, momentum, context);
                continue;
            }

            List<CompileDiagnostic> failures = addVectorGroup(velocityInputs, input, context);
            if (!failures.isEmpty()) {
                return new CompileResult.Failure<>(failures);
            }
            treeInputs.add(input);
        }

        ResolvedInputs resolved = new ResolvedInputs(List.copyOf(velocityInputs),
                List.copyOf(accelerationInputs), dynamic, List.copyOf(treeInputs));
        Gyromancy.LOGGER.debug(
                "[Momentum] resolve boundary={} dynamic={} velocity={} acceleration={}",
                boundary == null ? "none" : boundary.glyphId(), dynamic,
                resolved.velocityInputs().size(), resolved.accelerationInputs().size());
        return new CompileResult.Success<>(resolved);
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

    /**
     * Interprets a non-momentum child as a vector source. A failure is a hard
     * stage-2 rejection: the vector compiler is the authoritative second pass,
     * so an input it cannot compile must not be silently dropped. The runtime
     * error marker keeps this class of failure distinguishable from authored
     * structure errors.
     */
    private List<CompileDiagnostic> addVectorGroup(List<MomentumOp.VectorInput> result,
                                                   OpInput input, OpResolveContext context) {
        if (input instanceof OpInput.Op op && op.operator() instanceof MomentumOp) return List.of();

        if (input instanceof OpInput.RawGroup
                || input instanceof OpInput.Op op && op.sourceGroup() != null) {
            CompileResult<CompiledOp> compiled = vectorCompiler.compile(input, context);
            if (addCompiledVector(result, compiled)) return List.of();
            if (input instanceof OpInput.Op op && op.operator() instanceof VectorOp vector) {
                result.add(new MomentumOp.VectorInput(vector, MomentumOp.MotionMode.DIRECT));
                return List.of();
            }
            Gyromancy.LOGGER.debug("[Momentum] vector group rejected input={} result={}",
                    describe(input), compiled);
            List<CompileDiagnostic> diagnostics = new ArrayList<>();
            diagnostics.add(new CompileDiagnostic(CompileDiagnostic.RUNTIME_ERROR,
                    RUNTIME_ERROR_MESSAGE));
            if (input instanceof OpInput.RawGroup raw) diagnostics.addAll(raw.failures());
            if (compiled instanceof CompileResult.Failure<CompiledOp> failure) {
                diagnostics.addAll(failure.diagnostics());
            }
            return List.copyOf(diagnostics);
        }

        if (input instanceof OpInput.Op op && op.operator() instanceof VectorOp vector) {
            result.add(new MomentumOp.VectorInput(vector, MomentumOp.MotionMode.DIRECT));
        }
        return List.of();
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
                momentum.accelerationInputs(), momentum.dynamic(), List.of());
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
                          boolean dynamic,
                          List<OpInput> treeInputs) {
        ResolvedInputs {
            treeInputs = List.copyOf(treeInputs);
        }
    }
}
