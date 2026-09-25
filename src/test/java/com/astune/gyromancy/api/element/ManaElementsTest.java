package com.astune.gyromancy.api.element;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManaElementsTest {

    @Test
    void sumsAndScalesEverySlot() {
        double[] left = new double[ElementType.COUNT];
        left[ElementType.FIRE.ordinal()] = 2.0;
        double[] right = new double[ElementType.COUNT];
        right[ElementType.MANA.ordinal()] = 3.0;
        ManaElements sum = new ManaElements(left).plus(new ManaElements(right));

        assertEquals(2.0, sum.at(ElementType.FIRE));
        assertEquals(3.0, sum.at(ElementType.MANA));
        assertEquals(6.0, sum.scaled(2.0).at(ElementType.MANA));
    }

    @Test
    void roundsTripThroughCodec() {
        double[] values = new double[ElementType.COUNT];
        values[ElementType.WATER.ordinal()] = 1.5;
        ManaElements elements = new ManaElements(values);

        var json = ManaElements.CODEC.encodeStart(JsonOps.INSTANCE, elements).getOrThrow();
        ManaElements decoded = ManaElements.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(elements, decoded);
    }

    @Test
    void valuesAccessorIsDefensive() {
        ManaElements elements = ManaElements.EMPTY;
        elements.values()[0] = 12.0;

        assertTrue(elements.isEmpty());
        assertFalse(new ManaElements(new double[]{1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0})
                .isEmpty());
    }
}
