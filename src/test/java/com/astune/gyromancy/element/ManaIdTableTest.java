package com.astune.gyromancy.element;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.ink.InkRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManaIdTableTest {

    @Test
    void knownIdsResolveToTheirFixedRow() {
        int[] row = ManaIdTable.rowFor(InkRegistry.MANA_INK_ID);

        assertEquals(ElementType.COUNT, row.length);
        assertEquals(1, row[ElementType.MANA.ordinal()]);
        assertEquals(0, row[ElementType.FIRE.ordinal()]);
    }

    @Test
    void unknownIdsFallBackToTheZeroRow() {
        int[] row = ManaIdTable.rowFor(987_654);

        assertArrayEquals(new int[ElementType.COUNT], row);
    }

    @Test
    void registeredRowsUseElementOrder() {
        ManaIdTable.register(424_242, 0, 0, 0, 0, 0, 4, 0, 0, 0);

        int[] row = ManaIdTable.rowFor(424_242);
        assertEquals(4, row[ElementType.DARK.ordinal()]);
        assertEquals(0, row[ElementType.MANA.ordinal()]);
    }

    @Test
    void registerRejectsWrongSlotCount() {
        assertThrows(IllegalArgumentException.class,
                () -> ManaIdTable.register(424_243, 1, 2, 3));
    }
}
