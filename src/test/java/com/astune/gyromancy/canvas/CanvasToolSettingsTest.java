package com.astune.gyromancy.canvas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CanvasToolSettingsTest {
    @Test
    void penDiameterGrowsByTwoPerStepAndStaysOdd() {
        assertEquals(3, CanvasToolSettings.adjustedPenDiameter(1, 1.0));
        assertEquals(5, CanvasToolSettings.adjustedPenDiameter(3, 1.0));
        assertEquals(1, CanvasToolSettings.adjustedPenDiameter(3, -1.0));
        // Even inputs are clamped to an odd diameter before the step.
        assertEquals(7, CanvasToolSettings.adjustedPenDiameter(4, 1.0));
    }

    @Test
    void penDiameterIsClamped() {
        assertEquals(CanvasToolSettings.MAX_PEN_DIAMETER,
                CanvasToolSettings.adjustedPenDiameter(1, 100.0));
        assertEquals(CanvasToolSettings.MIN_PEN_DIAMETER,
                CanvasToolSettings.adjustedPenDiameter(31, -100.0));
        assertEquals(CanvasToolSettings.MIN_PEN_DIAMETER,
                CanvasToolSettings.clampPenDiameter(0));
        assertEquals(CanvasToolSettings.MAX_PEN_DIAMETER,
                CanvasToolSettings.clampPenDiameter(999));
    }

    @Test
    void stampScaleStepsExponentiallyAndIsClamped() {
        assertEquals(1.2, CanvasToolSettings.adjustedStampScale(1.0, 1.0), 1.0E-9);
        assertEquals(1.0, CanvasToolSettings.adjustedStampScale(1.2, -1.0), 1.0E-9);
        assertEquals(CanvasToolSettings.MAX_STAMP_SCALE,
                CanvasToolSettings.adjustedStampScale(1.0, 100.0), 1.0E-9);
        assertEquals(CanvasToolSettings.MIN_STAMP_SCALE,
                CanvasToolSettings.adjustedStampScale(1.0, -100.0), 1.0E-9);
    }

    @Test
    void invalidValuesFallBackToDefaults() {
        assertEquals(CanvasToolSettings.DEFAULT_STAMP_SCALE,
                CanvasToolSettings.clampStampScale(Double.NaN), 1.0E-9);
        assertEquals(CanvasToolSettings.DEFAULT_STAMP_SCALE,
                CanvasToolSettings.clampStampScale(Double.POSITIVE_INFINITY), 1.0E-9);
        assertEquals(CanvasToolSettings.DEFAULT_PEN_DIAMETER,
                CanvasToolSettings.adjustedPenDiameter(1, 0.0));
        // A zero scroll keeps the current (clamped) value untouched.
        assertEquals(2.0, CanvasToolSettings.adjustedStampScale(2.0, 0.0), 1.0E-9);
    }

    @Test
    void persistedDataClampsRestoredAndClientValues() {
        CanvasToolSettingsData clamped = new CanvasToolSettingsData(64, 99.0);

        assertEquals(CanvasToolSettings.MAX_PEN_DIAMETER, clamped.penDiameter());
        assertEquals(CanvasToolSettings.MAX_STAMP_SCALE, clamped.stampScale(), 1.0E-9);
        assertEquals(CanvasToolSettings.DEFAULT_STAMP_SCALE,
                new CanvasToolSettingsData(1, Double.NaN).stampScale(), 1.0E-9);
        assertEquals(CanvasToolSettings.DEFAULT_PEN_DIAMETER,
                CanvasToolSettingsData.DEFAULT.penDiameter());
        assertEquals(CanvasToolSettings.DEFAULT_STAMP_SCALE,
                CanvasToolSettingsData.DEFAULT.stampScale(), 1.0E-9);
        assertNotEquals(CanvasToolSettingsData.DEFAULT, clamped);
        assertEquals(3, CanvasToolSettingsData.DEFAULT.withPenDiameter(3).penDiameter());
        assertEquals(2.5,
                CanvasToolSettingsData.DEFAULT.withStampScale(2.5).stampScale(), 1.0E-9);
    }
}
