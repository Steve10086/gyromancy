package com.astune.gyromancy.compile.vector;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.CompileDiagnostic;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.compile.operator.OpResolveContext;
import com.astune.gyromancy.array.compile.VectorCompiler;
import com.astune.gyromancy.compile.operator.CompiledOp;

import java.util.ArrayList;
import java.util.List;

/** Compiles a vector operation's direct rune and group inputs. */
final class VectorInputCompiler {
    private VectorInputCompiler() {}

    static CompileResult<List<VectorComposition.Input>> all(PositionedGlyph boundary,
                                             List<OpInput> inputs,
                                             VectorCompiler compiler) {
        return all(boundary, inputs, compiler, OpResolveContext.forVector(boundary));
    }

    static CompileResult<List<VectorComposition.Input>> all(PositionedGlyph boundary,
                                             List<OpInput> inputs,
                                             VectorCompiler compiler,
                                             OpResolveContext context) {
        List<VectorComposition.Input> result = new ArrayList<>(direct(boundary, inputs));
        CompileResult<List<VectorComposition.Input>> grouped = groups(inputs, compiler, context);
        if (grouped instanceof CompileResult.Failure<List<VectorComposition.Input>> failure) {
            return new CompileResult.Failure<>(failure.diagnostics());
        }
        result.addAll(((CompileResult.Success<List<VectorComposition.Input>>) grouped).value());
        return new CompileResult.Success<>(List.copyOf(result));
    }

    static List<VectorComposition.Input> direct(PositionedGlyph boundary,
                                                List<OpInput> inputs) {
        List<VectorComposition.Input> result = new ArrayList<>();
        for (OpInput input : inputs) {
            if (!(input instanceof OpInput.Rune rune)) continue;
            StaticVectorDefinition.vectorFor(rune.symbolName(), boundary, rune.glyph())
                    .ifPresent(vector -> result.add(new VectorComposition.Input(
                            vector, mode(rune.symbolName()))));
        }
        return result;
    }

    static CompileResult<List<VectorComposition.Input>> groups(List<OpInput> inputs,
                                                VectorCompiler compiler) {
        return groups(inputs, compiler, OpResolveContext.forVector(null));
    }

    /**
     * Recursively compiles nested group inputs. A group the vector compiler
     * cannot turn into a vector is a hard rejection: silently dropping it
     * would let an authored structure disappear without any feedback.
     */
    static CompileResult<List<VectorComposition.Input>> groups(List<OpInput> inputs,
                                                VectorCompiler compiler,
                                                OpResolveContext context) {
        List<VectorComposition.Input> result = new ArrayList<>();
        for (OpInput input : inputs) {
            if (!(input instanceof OpInput.RawGroup)
                    && !(input instanceof OpInput.Op)) continue;
            CompileResult<CompiledOp> compiled = compiler.compile(input, context);
            if (compiled instanceof CompileResult.Success<CompiledOp> success
                    && success.value() instanceof VectorOp vector) {
                result.add(new VectorComposition.Input(vector, VectorComposition.Mode.DIRECT));
            } else if (input instanceof OpInput.Op op
                    && op.operator() instanceof VectorOp vector) {
                result.add(new VectorComposition.Input(vector, VectorComposition.Mode.DIRECT));
            } else {
                Gyromancy.LOGGER.debug("[Vector] group rejected input={} result={}",
                        describe(input), compiled);
                return new CompileResult.Failure<>(
                        compiled instanceof CompileResult.Failure<CompiledOp> failure
                                ? failure.diagnostics()
                                : List.of(new CompileDiagnostic("missing_vector_source_group",
                                        "Vector compilation requires a group input")));
            }
        }
        return new CompileResult.Success<>(List.copyOf(result));
    }

    private static String describe(OpInput input) {
        return switch (input) {
            case OpInput.RawGroup raw -> "RawGroup(" + raw.boundary().symbolId().getPath()
                    + "#" + raw.boundary().glyphId() + ")";
            case OpInput.Op op -> "Op(" + op.operator().getClass().getSimpleName() + ")";
            case OpInput.Rune rune -> "Rune(" + rune.symbolName() + ")";
        };
    }

    private static VectorComposition.Mode mode(String runeName) {
        return "arrow".equals(runeName)
                ? VectorComposition.Mode.TANGENTIAL
                : VectorComposition.Mode.DIRECT;
    }
}
