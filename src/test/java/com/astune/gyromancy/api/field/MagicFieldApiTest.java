package com.astune.gyromancy.api.field;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MagicFieldApiTest {
    @Test
    void directionNormalizesItsVectorAndAngleWithoutChangingShapeData() {
        FieldDirection direction = new FieldDirection(new Vec3(0.0, 0.0, 4.0F), -90.0F);

        assertEquals(0.0, direction.vector().x, 1.0E-12);
        assertEquals(0.0, direction.vector().y, 1.0E-12);
        assertEquals(1.0, direction.vector().z, 1.0E-12);
        assertEquals(270.0F, direction.angleDegrees());
    }

    @Test
    void shapeDefaultsAverageEnergyToEnergyDensity() {
        MagicFieldShape shape = new MagicFieldShape() {
            @Override
            public AABB bounds() {
                return new AABB(-1.0, -1.0, -1.0, 1.0, 1.0, 1.0);
            }

            @Override
            public boolean isInside(Vec3 point) {
                return bounds().contains(point);
            }

            @Override
            public double volume() {
                return 8.0;
            }
        };

        assertEquals(2.5, shape.averageEnergy(20.0));
    }

    @Test
    void directionRejectsAZeroVector() {
        assertThrows(IllegalArgumentException.class,
                () -> new FieldDirection(Vec3.ZERO, 0.0F));
    }
}
