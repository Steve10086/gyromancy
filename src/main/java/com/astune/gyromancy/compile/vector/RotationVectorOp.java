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
        this(id, boundary, rawInputs, color, axisInputs, vectorInputs, rotationSpeed, 1.0, false);
    }

    RotationVectorOp(ResourceLocation id, PositionedGlyph boundary, List<OpInput> rawInputs,
                     int color, List<VectorComposition.Input> axisInputs,
                     List<VectorComposition.Input> vectorInputs, double rotationSpeed,
                     double scale, boolean reverted) {
        super(id, boundary, rawInputs, color, scale, reverted);
        this.axisInputs = List.copyOf(axisInputs);
        this.vectorInputs = List.copyOf(vectorInputs);
        this.rotationSpeed = rotationSpeed;
    }

    @Override
    protected Vec3 provideVector(VectorContext context) {
        // Only the authored nested vector may be rotated. Never fall back to
        // the entity's own facing or velocity: that would let the array
        // synthesize motion out of nothing and feed its own state back in.
        Vec3 result = VectorComposition.compose(context, vectorInputs);
        if (result.lengthSqr() < 1.0E-8) return Vec3.ZERO;

        Vec3 axis = VectorComposition.compose(context, axisInputs);
        if (axis.lengthSqr() < 1.0E-8) axis = VectorFrameMath.movementAxis(context);
        if (axis.lengthSqr() < 1.0E-8) return Vec3.ZERO;

        double speed = reverted() ? -rotationSpeed : rotationSpeed;
        double radians = Math.toRadians(speed * context.tick());
        Vec3 rotated = VectorFrameMath.rotateAroundAxis(result, axis, radians);
        if (context.tick() < 12) {
            com.astune.gyromancy.Gyromancy.LOGGER.debug(
                    "[Rotation] tick={} nested={} axis={} speed={} rotated={}",
                    context.tick(), result, axis, rotationSpeed, rotated);
        }
        return rotated;
    }

    @Override
    protected Vec3 applyRevert(Vec3 vector) {
        return vector;
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
