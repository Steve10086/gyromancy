package com.astune.gyromancy.api.element;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElementConcentrationsTest {

    @Test
    void bellDecayAddsLogDecayAboveThreshold() {
        long thresholdDecay = ElementConcentrations.bellDecay(50_000);
        assertEquals(50, thresholdDecay);
        assertTrue(ElementConcentrations.bellDecay(50_001) > thresholdDecay);
    }
}
