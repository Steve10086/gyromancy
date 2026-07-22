package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.compile.operator.Operator;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public interface ArrayEffectDefinition {
    ResourceLocation id();

    List<OpInputMatcher> match();

    CompileResult<Operator> compile(PositionedGlyph boundary, List<OpInput> matchedInputs, List<OpInput> inputs);
}
