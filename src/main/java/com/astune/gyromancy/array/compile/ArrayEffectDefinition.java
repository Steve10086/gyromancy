package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public interface ArrayEffectDefinition {
    ResourceLocation id();

    List<OpInputMatcher> match();

    CompileResult<CompiledArrayNode> compile(PositionedGlyph boundary, List<OpInput> inputs);
}
