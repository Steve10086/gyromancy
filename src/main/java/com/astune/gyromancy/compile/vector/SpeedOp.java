package com.astune.gyromancy.compile.vector;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.OpInput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class SpeedOp extends VectorOp {
    SpeedOp(ResourceLocation id, PositionedGlyph boundary, List<OpInput> inputs, int color) {
        super(id, boundary, inputs, color);
    }

    @Override
    public Vec3 provide(VectorContext context) {
        return context == null ? Vec3.ZERO : context.velocity();
    }
}
