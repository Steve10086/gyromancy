package com.astune.gyromancy.canvas;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanvasToolSettingsTest {
    @Test
    void penDiameterIsOddAndStoredPerCharacter() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertEquals(1, CanvasToolSettings.penDiameter(first));
        assertEquals(3, CanvasToolSettings.adjustPenDiameter(first, 1.0));
        assertEquals(5, CanvasToolSettings.adjustPenDiameter(first, 1.0));
        assertEquals(5, CanvasToolSettings.penDiameter(first));
        assertEquals(1, CanvasToolSettings.penDiameter(second));
    }

    @Test
    void penDiameterIsClamped() {
        UUID player = UUID.randomUUID();

        assertEquals(31, CanvasToolSettings.adjustPenDiameter(player, 100.0));
        assertEquals(1, CanvasToolSettings.adjustPenDiameter(player, -100.0));
    }

    @Test
    void stampScaleIsStoredAndClampedPerCharacter() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertEquals(1.0, CanvasToolSettings.stampScale(first), 1.0E-9);
        assertEquals(1.2,
                CanvasToolSettings.adjustStampScale(first, 1.0), 1.0E-9);
        assertEquals(1.2, CanvasToolSettings.stampScale(first), 1.0E-9);
        assertEquals(1.0, CanvasToolSettings.stampScale(second), 1.0E-9);
        assertEquals(CanvasToolSettings.MAX_STAMP_SCALE,
                CanvasToolSettings.adjustStampScale(first, 100.0), 1.0E-9);
        assertEquals(CanvasToolSettings.MIN_STAMP_SCALE,
                CanvasToolSettings.adjustStampScale(first, -100.0), 1.0E-9);
    }
}
