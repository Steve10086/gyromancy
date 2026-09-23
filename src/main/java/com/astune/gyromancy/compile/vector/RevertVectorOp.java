package com.astune.gyromancy.compile.vector;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.OpInput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Reverses the vector produced by its nested vector composition. */
public final class RevertVectorOp extends VectorOp {
    private final List<VectorComposition.Input> vectorInputs;

    RevertVectorOp(ResourceLocation id, PositionedGlyph boundary, List<OpInput> rawInputs,
                   int color, List<VectorComposition.Input> vectorInputs) {
        super(id, boundary, rawInputs, color);
        this.vectorInputs = List.copyOf(vectorInputs);
    }

    @Override
    public Vec3 provide(VectorContext context) {
        Vec3 composed = VectorComposition.compose(context, vectorInputs);
        if (context != null && context.tick() < 12) {
            com.astune.gyromancy.Gyromancy.LOGGER.debug(
                    "[Revert] tick={} composed={} parts={}", context.tick(), composed,
                    vectorInputs.stream()
                            .map(input -> input.vector().getClass().getSimpleName()
                                    + ":" + input.mode())
                            .toList());
        }
        return composed.scale(-1.0);
    }

    List<VectorComposition.Input> vectorInputs() {
        return vectorInputs;
    }
}
