package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.LocalCompileContext;
import com.astune.gyromancy.array.compile.LocalCompileResult;
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

    /**
     * Stage-2 pass. The default implementation propagates the recursion into
     * every direct child and rebuilds this node from the materialized inputs.
     * Special compilers (vector clusters, shape decoding, ...) override it.
     */
    default LocalCompileResult localCompile(LocalCompileContext context) {
        List<OpInput> materialized = new ArrayList<>(inputs().size());
        for (OpInput input : inputs()) materialized.add(context.materialize(input));
        return LocalCompileResult.success(copyWithInputs(materialized));
    }

    /**
     * Rebuilds this node with its materialized direct inputs. Leaf nodes which
     * never read {@link #inputs()} at runtime keep the identity default; every
     * node whose runtime behaviour iterates its children must override it.
     */
    default CompiledOp copyWithInputs(List<OpInput> inputs) {
        return this;
    }
}
