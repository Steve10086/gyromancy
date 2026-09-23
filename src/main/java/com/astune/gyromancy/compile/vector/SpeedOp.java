package com.astune.gyromancy.compile.vector;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.OpInput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class SpeedOp extends VectorOp {
    SpeedOp(ResourceLocation id, PositionedGlyph boundary, List<OpInput> inputs, int color) {
        this(id, boundary, inputs, color, 1.0, false);
    }

    SpeedOp(ResourceLocation id, PositionedGlyph boundary, List<OpInput> inputs, int color,
            double scale, boolean reverted) {
        super(id, boundary, inputs, color, scale, reverted);
    }

    @Override
    protected Vec3 provideVector(VectorContext context) {
        return context == null ? Vec3.ZERO : context.velocity();
    }
}
