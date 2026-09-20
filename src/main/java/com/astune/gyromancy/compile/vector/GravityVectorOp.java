package com.astune.gyromancy.compile.vector;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.OpInput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Provides the owning entity's current world-gravity acceleration. */
public final class GravityVectorOp extends VectorOp {
    GravityVectorOp(ResourceLocation id, PositionedGlyph boundary,
                     List<OpInput> inputs, int color) {
        super(id, boundary, inputs, color);
    }

    @Override
    public Vec3 provide(VectorContext context) {
        double gravity = context == null ? 0.0 : context.gravity();
        return Double.isFinite(gravity) && gravity != 0.0
                ? new Vec3(0.0, -gravity, 0.0)
                : Vec3.ZERO;
    }
}
