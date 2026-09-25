package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.element.ManaElements;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.LocalCompileContext;
import com.astune.gyromancy.array.compile.LocalCompileResult;
import com.astune.gyromancy.array.compile.OpInput;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public interface CompiledOp {
    /** Default cost of one compiled operator: ten mana, no other elements. */
    ManaElements DEFAULT_COST = defaultCost();

    ResourceLocation id();

    PositionedGlyph boundary();

    /**
     * Direct entries of this operator. An entry is either authored data
     * ({@link OpInput.Rune}, {@link OpInput.RawGroup}) or an executable child
     * ({@link OpInput.Op}); the final Op tree is the subgraph induced by the
     * {@code OpInput.Op} edges, so every compiled operator is listed exactly
     * once.
     */
    List<OpInput> inputs();

    int color();

    /** The elements this operator consumes when its array activates. */
    default ManaElements getCost() {
        return DEFAULT_COST;
    }

    private static ManaElements defaultCost() {
        double[] values = new double[ElementType.COUNT];
        values[ElementType.MANA.ordinal()] = 10.0;
        return new ManaElements(values);
    }

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
