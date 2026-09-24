package com.astune.gyromancy.compile.vector;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.compile.operator.CompiledOp;
import com.astune.gyromancy.compile.operator.OpResolveContext;
import com.astune.gyromancy.compile.operator.RotationOp;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

final class RotationVectorDefinition {
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            Gyromancy.MODID, "vector_rotation");
    public static final double ROTATION_SPEED_SCALE = 10;
    static final OpDefinition DEFINITION = new OpDefinition() {
        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public List<OpInputMatcher> match() {
            return List.of(OpInputMatcher.rune("loop"));
        }

        @Override
        public List<OpInputMatcher> accepted() {
            return VectorDefinitionSupport.accepted(false);
        }

            @Override
            public CompileResult<CompiledOp> compile(PositionedGlyph boundary,
                                                 List<OpInput> matchedInputs,
                                                 List<OpInput> inputs) {
                return compile(OpResolveContext.forVector(boundary), boundary, matchedInputs, inputs);
            }

            @Override
            public CompileResult<CompiledOp> compile(OpResolveContext context,
                                                     PositionedGlyph boundary,
                                                     List<OpInput> matchedInputs,
                                                     List<OpInput> inputs) {
                OpResolveContext effectiveContext = context == null
                        ? OpResolveContext.forVector(boundary) : context;
                PositionedGlyph loop = ((OpInput.Rune) matchedInputs.getFirst()).glyph();
                CompileResult<List<VectorComposition.Input>> resolved = VectorInputCompiler.groups(
                        inputs, VectorDefinitionSupport.compiler(), effectiveContext);
                if (resolved instanceof CompileResult.Failure<
                        List<VectorComposition.Input>> failure) {
                    return new CompileResult.Failure<>(failure.diagnostics());
                }
                return new CompileResult.Success<>(new RotationVectorOp(
                        ID, boundary, inputs, 0,
                        ((CompileResult.Success<List<VectorComposition.Input>>) resolved).value(),
                        VectorInputCompiler.direct(boundary, inputs),
                        loop.length() * ROTATION_SPEED_SCALE,
                        VectorOp.secretScale(inputs),
                        VectorDefinitionSupport.containsRune(inputs, "revert")));
            }
    };

    private RotationVectorDefinition() {}
}
