package com.astune.gyromancy.compile.vector;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
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

    static List<VectorComposition.Input> all(PositionedGlyph boundary,
                                             List<OpInput> inputs,
                                             VectorCompiler compiler) {
        return all(boundary, inputs, compiler, OpResolveContext.forVector(boundary));
    }

    static List<VectorComposition.Input> all(PositionedGlyph boundary,
                                             List<OpInput> inputs,
                                             VectorCompiler compiler,
                                             OpResolveContext context) {
        List<VectorComposition.Input> result = direct(boundary, inputs);
        result.addAll(groups(inputs, compiler, context));
        return List.copyOf(result);
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

    static List<VectorComposition.Input> groups(List<OpInput> inputs, VectorCompiler compiler) {
        return groups(inputs, compiler, OpResolveContext.forVector(null));
    }

    static List<VectorComposition.Input> groups(List<OpInput> inputs,
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
            }
        }
        return result;
    }

    private static VectorComposition.Mode mode(String runeName) {
        return "arrow".equals(runeName)
                ? VectorComposition.Mode.TANGENTIAL
                : VectorComposition.Mode.DIRECT;
    }
}
