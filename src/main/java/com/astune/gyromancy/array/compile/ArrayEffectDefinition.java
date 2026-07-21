package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public interface ArrayEffectDefinition {
    ResourceLocation id();

    List<String> symbols();

    ElementType primaryElement(String symbol);

    CompiledArrayNode compile(PositionedGlyph boundary,
                              ElementType primaryElement,
                              EffectAttributes attributes,
                              List<CompiledArrayNode> children);
}
