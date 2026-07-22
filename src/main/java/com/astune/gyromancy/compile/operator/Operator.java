package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.array.compile.OpInput;
import com.astune.gyromancy.array.runtime.RuntimeHandle;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;

public interface Operator {
    ResourceLocation id();

    PositionedGlyph boundary();

    List<OpInput> inputs();

    RuntimeHandle activate(ServerLevel level);

    void deactivate(ServerLevel level, Map<String, Object> scratchData);

    int color();
}
