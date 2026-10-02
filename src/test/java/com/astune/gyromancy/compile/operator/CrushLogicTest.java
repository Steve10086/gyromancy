package com.astune.gyromancy.compile.operator;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrushLogicTest {

    @Test
    void averageEarthConvertsToWholeStrengthLevels() {
        assertEquals(0, CrushLogic.strengthForAverage(0.0));
        assertEquals(0, CrushLogic.strengthForAverage(999.9));
        assertEquals(1, CrushLogic.strengthForAverage(1000.0));
        assertEquals(2, CrushLogic.strengthForAverage(2999.9));
        assertEquals(0, CrushLogic.strengthForAverage(Double.NaN));
    }

    @Test
    void outermostShellIsLevelOneAndStrengthRaisesEveryShell() {
        assertEquals(1, CrushLogic.shellLevel(4.0, 3.5, 0));
        assertEquals(2, CrushLogic.shellLevel(4.0, 2.5, 0));
        assertEquals(5, CrushLogic.shellLevel(4.0, 0.0, 0));
        assertEquals(7, CrushLogic.shellLevel(4.0, 0.0, 2));
        assertEquals(4, CrushLogic.shellLevel(4.0, 2.5, 2));
        assertEquals(6, CrushLogic.shellLevel(4.0, 3.5, 5));
    }

    @Test
    void momentumResidualRaisesTheCentreFromMinusRadiusTowardsPlusRadius() {
        assertEquals(-4.0, CrushLogic.centerOffset(0.0, 4.0), 1.0E-9);
        assertEquals(-2.0, CrushLogic.centerOffset(2.0, 4.0), 1.0E-9);
        assertEquals(0.0, CrushLogic.centerOffset(4.0, 4.0), 1.0E-9);
        assertEquals(4.0, CrushLogic.centerOffset(10.0, 4.0), 1.0E-9);
        assertEquals(-4.0, CrushLogic.centerOffset(Double.NaN, 4.0), 1.0E-9);
    }

    @Test
    void upwardComponentIsTheMomentumResidualOfTheArrowSum() {
        // A single in-plane arrow sums exactly to its own length: no residual.
        assertEquals(0.0,
                CrushLogic.upwardComponent(2.0, new Vec3(2.0, 0.0, 0.0)), 1.0E-9);
        // Two opposing length-two arrows cancel out completely.
        assertEquals(4.0,
                CrushLogic.upwardComponent(4.0, Vec3.ZERO), 1.0E-9);
        // An in-plane arrow plus a same-length arrow_up leaves a normal residual.
        assertEquals(2.0 - Math.sqrt(2.0),
                CrushLogic.upwardComponent(2.0, new Vec3(1.0, 0.0, 1.0)), 1.0E-9);
        // No arrows means no residual.
        assertEquals(0.0, CrushLogic.upwardComponent(0.0, Vec3.ZERO), 1.0E-9);
    }
}
