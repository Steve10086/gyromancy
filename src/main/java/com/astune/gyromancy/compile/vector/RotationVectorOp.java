package com.astune.gyromancy.compile.vector;

import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.array.compile.OpInput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Rotates a nested vector around direct arrow-defined axes over runtime ticks. */
public final class RotationVectorOp extends VectorOp {
    private final List<VectorComposition.Input> axisInputs;
    private final List<VectorComposition.Input> vectorInputs;
    private final double rotationSpeed;

    RotationVectorOp(ResourceLocation id, PositionedGlyph boundary, List<OpInput> rawInputs,
                     int color, List<VectorComposition.Input> axisInputs,
                     List<VectorComposition.Input> vectorInputs, double rotationSpeed) {
        super(id, boundary, rawInputs, color);
        this.axisInputs = List.copyOf(axisInputs);
        this.vectorInputs = List.copyOf(vectorInputs);
        this.rotationSpeed = rotationSpeed;
    }

    @Override
    public Vec3 provide(VectorContext context) {
        Vec3 result = VectorComposition.compose(context, vectorInputs);
        if (result.lengthSqr() < 1.0E-8) result = context.velocity();
        if (result.lengthSqr() < 1.0E-8) result = context.facing();

        Vec3 axis = VectorComposition.compose(context, axisInputs);
        if (axis.lengthSqr() < 1.0E-8) axis = VectorFrameMath.movementAxis(context);
        if (axis.lengthSqr() < 1.0E-8) return result;

        double radians = Math.toRadians(rotationSpeed * context.tick());
        return VectorFrameMath.rotateAroundAxis(result, axis, radians);
    }

    List<VectorComposition.Input> axisInputs() {
        return axisInputs;
    }

    List<VectorComposition.Input> vectorInputs() {
        return vectorInputs;
    }

    double rotationSpeed() {
        return rotationSpeed;
    }
}
