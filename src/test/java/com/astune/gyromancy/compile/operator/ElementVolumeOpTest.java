package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.util.MagicBallGeometry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ElementVolumeOpTest {
    private static final double EPSILON = 1.0e-9;

    @Test
    void concentrationCapsManaGrowthBudget() {
        ElementVolumeOp op = new ElementVolumeOp(ElementType.FIRE, "storedMana", 10,
                0.1, 100.0, 2000.0, 0.01);

        assertEquals(50L, op.manaBudget(1.0, 3000.0));
        assertEquals(0L, op.manaBudget(1.0, 1999.0));
        assertEquals(1.5, op.grownVolume(1.0, 50L), EPSILON);
    }

    @Test
    void insufficientElementLosesIntervalAdjustedVolumeWithoutPassingSpawnFloor() {
        ElementVolumeOp op = new ElementVolumeOp(ElementType.FIRE, "storedMana", 10,
                0.1, 100.0, 2000.0, 0.01);

        assertEquals(1.01, op.lostVolume(2.0, 0.0), EPSILON);

        double spawnVolume = MagicBallGeometry.volume(0.1F);
        assertEquals(0.0, op.lostVolume(spawnVolume, 0.0), EPSILON);
    }

    @Test
    void sufficientElementPreventsVolumeLoss() {
        ElementVolumeOp op = new ElementVolumeOp(ElementType.FIRE, "storedMana", 10,
                0.1, 100.0, 2000.0, 0.01);

        assertEquals(0.0, op.lostVolume(2.0, 200.0), EPSILON);
    }

    @Test
    void firePayloadReservesGrowthManaBeforeConvertingTheRemainder() {
        var payload = FireProjectileOp.defaultPayload();

        assertInstanceOf(ExplosionOp.class, payload.get(0));
        assertInstanceOf(ElementVolumeOp.class, payload.get(1));
        assertInstanceOf(ElementConversionOp.class, payload.get(2));
    }
}
