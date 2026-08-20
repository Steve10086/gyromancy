package com.astune.gyromancy.compile.vector;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.OpInput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Provides the current array normal, with the available frame as fallback. */
public final class ArrayNormalVectorOp extends VectorOp {
    private final double scale;

    public ArrayNormalVectorOp(ResourceLocation id, PositionedGlyph boundary, List<OpInput> inputs,
                               int color, double scale) {
        super(id, boundary, inputs, color);
        this.scale = scale;
    }

    public double scale() { return scale; }

    @Override
    public Vec3 provide(VectorContext context) {
        Vec3 normal = context.liveFrame()
                .map(frame -> frame.normal())
                .orElseGet(() -> context.arrayNormal().lengthSqr() > 1.0E-8
                        ? context.arrayNormal()
                        : context.activationFrame()
                        .or(() -> context.compileFrame())
                        .map(frame -> frame.normal())
                        .orElse(Vec3.ZERO));
        return normal.lengthSqr() < 1.0E-8 ? Vec3.ZERO : normal.normalize().scale(scale);
    }
}
