package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.OpInput;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public interface CompiledOp {
    ResourceLocation id();

    PositionedGlyph boundary();

    List<OpInput> inputs();

    int color();

    default List<CompiledOp> childOps() {
        List<CompiledOp> children = new ArrayList<>();
        for (OpInput input : inputs()) {
            if (input instanceof OpInput.Op op) children.add(op.operator());
        }
        return List.copyOf(children);
    }
}
