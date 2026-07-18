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

    @Test
    void keepsWaterInFormerWoodSlot() {
        assertEquals(9, ElementType.COUNT);
        assertEquals(2, ElementType.WATER.ordinal());
        assertEquals(8, ElementType.MANA.ordinal());

        long[] values = new long[ElementType.COUNT];
        values[ElementType.WATER.ordinal()] = 24;
        values[ElementType.MANA.ordinal()] = 42;

        ElementConcentrations concentrations = new ElementConcentrations(values, new long[0]);

        assertEquals(24, concentrations.get(ElementType.WATER));
        assertEquals(42, concentrations.get(ElementType.MANA));
        assertEquals(ElementType.COUNT, concentrations.values().length);
        assertEquals(ElementType.COUNT, concentrations.derivatives().length);
    }
}
