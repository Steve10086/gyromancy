package com.astune.gyromancy.compile.vector;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.compile.operator.CompiledOp;
import com.astune.gyromancy.compile.operator.OpResolveContext;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** The vector meaning of revert: negate its nested composition. */
final class RevertVectorDefinition {
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            Gyromancy.MODID, "vector_revert");

    static final OpDefinition DEFINITION = new OpDefinition() {
        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public List<OpInputMatcher> match() {
            return List.of(OpInputMatcher.rune("revert"));
        }

        @Override
        public List<OpInputMatcher> accepted() {
            return VectorDefinitionSupport.accepted(true);
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
            return new CompileResult.Success<>(new RevertVectorOp(
                    ID, boundary, inputs, 0,
                    VectorInputCompiler.all(boundary, inputs,
                            VectorDefinitionSupport.compiler(), effectiveContext)));
        }
    };

    private RevertVectorDefinition() {}
}
