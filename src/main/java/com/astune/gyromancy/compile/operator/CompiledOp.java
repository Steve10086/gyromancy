package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.runtime.OpRuntimeContext;
import com.astune.gyromancy.array.compile.OpInput;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public interface CompiledOp {
    ResourceLocation id();

    PositionedGlyph boundary();

    List<OpInput> inputs();

    int color();

    /** Applies an emission modifier with the current runtime context. */
    default EmitOp.Emission modifyEntityEmission(EmitOp.Emission emission,
                                                  OpRuntimeContext context) {
        return emission;
    }

    /** Allows payload contributors to materialize data against the current context. */
    default void contributeEntityPayloads(List<EntityPayload> payloads,
                                          OpRuntimeContext context) {
    }

    default List<CompiledOp> childOps() {
        List<CompiledOp> children = new ArrayList<>();
        for (OpInput input : inputs()) {
            if (input instanceof OpInput.Op op) children.add(op.operator());
        }
        return List.copyOf(children);
    }
}
