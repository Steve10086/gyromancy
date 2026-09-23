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

    /**
     * Adds every input vector, projecting {@link Mode#TANGENTIAL} inputs onto
     * the array plane first. Opposing vectors simply sum to their remainder.
     */
    static Vec3 compose(VectorContext context, List<Input> inputs) {
        // The tangential projection uses the array plane. Never use the
        // entity's velocity/facing as the primary reference: the array output
        // must not depend on or feed back into the entity's own motion.
        Vec3 axis = directionOrZero(context.arrayNormal());
        if (axis.lengthSqr() < EPSILON) axis = directionOrZero(context.velocity());
        if (axis.lengthSqr() < EPSILON) axis = directionOrZero(context.facing());

        Vec3 sum = Vec3.ZERO;
        for (Input input : inputs) {
            Vec3 vector = input.vector().provide(context);
            if (vector == null || vector.lengthSqr() < EPSILON) continue;

            if (input.mode() == Mode.TANGENTIAL) {
                if (axis.lengthSqr() < EPSILON) continue;
                vector = vector.subtract(axis.scale(vector.dot(axis)));
                if (vector.lengthSqr() < EPSILON) continue;
            }
            sum = sum.add(vector);
        }
        return sum;
    }

    static Vec3 directionOrZero(Vec3 vector) {
        return vector == null || vector.lengthSqr() < EPSILON ? Vec3.ZERO : vector.normalize();
    }
}
