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

final class SpeedVectorDefinition {
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            Gyromancy.MODID, "vector_speed");

    static final OpDefinition DEFINITION = new OpDefinition() {
        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public List<OpInputMatcher> match() {
            return List.of(OpInputMatcher.rune("engaging"), OpInputMatcher.rune("curl"));
        }

        @Override
        public List<OpInputMatcher> accepted() {
            return List.of();
        }

        @Override
        public CompileResult<CompiledOp> compile(PositionedGlyph boundary,
                                                  List<OpInput> matchedInputs,
                                                  List<OpInput> inputs) {
            return new CompileResult.Success<>(new SpeedOp(ID, boundary, inputs, 0));
        }
    };

    private SpeedVectorDefinition() {}
}
