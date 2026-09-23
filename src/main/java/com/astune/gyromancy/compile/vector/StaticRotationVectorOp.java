package com.astune.gyromancy.compile.vector;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.OpInput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Converts a composed target direction into a world-space axis-angle vector. */
public final class StaticRotationVectorOp extends VectorOp {
    private final List<VectorComposition.Input> inputs;
    private final boolean curl;

    StaticRotationVectorOp(ResourceLocation id, PositionedGlyph boundary, List<OpInput> rawInputs,
                           int color, List<VectorComposition.Input> inputs, boolean curl) {
        this(id, boundary, rawInputs, color, inputs, curl, 1.0, false);
    }

    StaticRotationVectorOp(ResourceLocation id, PositionedGlyph boundary, List<OpInput> rawInputs,
                           int color, List<VectorComposition.Input> inputs, boolean curl,
                           double scale, boolean reverted) {
        super(id, boundary, rawInputs, color, scale, reverted);
        this.inputs = List.copyOf(inputs);
        this.curl = curl;
    }

    @Override
    protected Vec3 provideVector(VectorContext context) {
        Vec3 target = VectorComposition.compose(context, inputs);
        if (curl) target = VectorFrameMath.orientByMovement(target, context);
        if (reverted()) target = target.scale(-1.0);
        return VectorFrameMath.worldYRotationTo(target);
    }

    @Override
    protected Vec3 applyRevert(Vec3 vector) {
        return vector;
    }

    List<VectorComposition.Input> vectorInputs() {
        return inputs;
    }

    boolean curl() {
        return curl;
    }
}
