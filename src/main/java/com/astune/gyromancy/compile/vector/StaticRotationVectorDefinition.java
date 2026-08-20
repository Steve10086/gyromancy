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

final class StaticRotationVectorDefinition {
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            Gyromancy.MODID, "vector_static_rotation");

    static final OpDefinition DEFINITION = new OpDefinition() {
        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public List<OpInputMatcher> match() {
            return List.of(OpInputMatcher.rune("drain"));
        }

        @Override
        public List<OpInputMatcher> accepted() {
            return VectorDefinitionSupport.accepted(true);
        }

        @Override
        public CompileResult<CompiledOp> compile(PositionedGlyph boundary,
                                                 List<OpInput> matchedInputs,
                                                 List<OpInput> inputs) {
            return new CompileResult.Success<>(new StaticRotationVectorOp(
                    ID, boundary, inputs, 0,
                    VectorInputCompiler.all(boundary, inputs, VectorDefinitionSupport.compiler()),
                    VectorDefinitionSupport.containsRune(inputs, "curl")));
        }
    };

    private StaticRotationVectorDefinition() {}
}
