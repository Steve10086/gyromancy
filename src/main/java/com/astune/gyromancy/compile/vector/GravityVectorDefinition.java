package com.astune.gyromancy.compile.vector;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.CompileResult;
import com.astune.gyromancy.array.compile.OpDefinition;
import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.compile.OpInputMatcher;
import com.astune.gyromancy.compile.operator.CompiledOp;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** The standalone vector meaning of the space rune. */
final class GravityVectorDefinition {
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            Gyromancy.MODID, "vector_gravity");

    static final OpDefinition DEFINITION = new OpDefinition() {
        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public List<OpInputMatcher> match() {
            return List.of(OpInputMatcher.rune("space"));
        }

        @Override
        public List<OpInputMatcher> accepted() {
            return VectorDefinitionSupport.modifiers();
        }

        @Override
        public CompileResult<CompiledOp> compile(PositionedGlyph boundary,
                                                  List<OpInput> matchedInputs,
                                                  List<OpInput> inputs) {
            return new CompileResult.Success<>(new GravityVectorOp(
                    ID, boundary, inputs, 0,
                    VectorOp.secretScale(inputs),
                    VectorDefinitionSupport.containsRune(inputs, "revert")));
        }
    };

    private GravityVectorDefinition() {}
}
