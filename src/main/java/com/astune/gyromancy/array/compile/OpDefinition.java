package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.compile.operator.CompiledOp;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public interface OpDefinition {
    ResourceLocation id();

    List<OpInputMatcher> match();

    CompileResult<CompiledOp> compile(PositionedGlyph boundary, List<OpInput> matchedInputs, List<OpInput> inputs);
}
