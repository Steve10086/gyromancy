package com.astune.gyromancy.compile.vector;

import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Shared vector summation rules for VectorOps. */
final class VectorComposition {
    private static final double EPSILON = 1.0E-8;

    private VectorComposition() {}

    enum Mode {
        DIRECT,
        TANGENTIAL
    }

    record Input(VectorOp vector, Mode mode) {
        Input {
            if (vector == null || mode == null) {
                throw new IllegalArgumentException("Vector composition inputs need a VectorOp and mode");
            }
        }
    }

    static Vec3 compose(VectorContext context, List<Input> inputs) {
        Vec3 reference = directionOrZero(context.velocity());
        if (reference.lengthSqr() < EPSILON) reference = directionOrZero(context.facing());
        if (reference.lengthSqr() < EPSILON) reference = directionOrZero(context.arrayNormal());

        Vec3 result = Vec3.ZERO;
        for (Input input : inputs) {
            Vec3 vector = input.vector().provide(context);
            if (vector == null || vector.lengthSqr() < EPSILON) continue;

            double magnitude = vector.length();
            if (input.mode() == Mode.TANGENTIAL) {
                if (reference.lengthSqr() < EPSILON) continue;
                vector = vector.subtract(reference.scale(vector.dot(reference)));
                if (vector.lengthSqr() < EPSILON) continue;
            }
            result = result.add(vector.normalize().scale(magnitude));
        }
        return result;
    }

    static Vec3 directionOrZero(Vec3 vector) {
        return vector == null || vector.lengthSqr() < EPSILON ? Vec3.ZERO : vector.normalize();
    }
}
