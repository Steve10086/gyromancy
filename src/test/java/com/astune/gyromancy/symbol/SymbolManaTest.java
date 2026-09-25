package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.element.ManaElements;
import com.astune.gyromancy.element.ManaIdTable;
import com.astune.gyromancy.ink.InkRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymbolManaTest {

    @Test
    void solvesCountsThroughTheTableAndPixelArea() {
        ManaElements solved = SymbolMana.solve(
                Map.of(InkRegistry.MANA_INK_ID, 3), 2.0);

        assertEquals(6.0, solved.at(ElementType.MANA));
        assertEquals(0.0, solved.at(ElementType.FIRE));
    }

    @Test
    void unknownIdsFallBackAndStillContributeTheirRow() {
        int[] fallback = ManaIdTable.rowFor(ManaIdTable.FALLBACK_ID);

        ManaElements solved = SymbolMana.solve(Map.of(555_555, 5), 1.0);

        for (ElementType element : ElementType.values()) {
            assertEquals(5.0 * fallback[element.ordinal()], solved.at(element));
        }
    }

    @Test
    void emptyCountsSolveToEmptyElements() {
        assertTrue(SymbolMana.solve(Map.of(), 4.0).isEmpty());
    }

    @Test
    void dominantIdPicksTheMostFrequentId() {
        int[] effects = {20, 20, 7, 0};

        int dominant = SymbolMana.dominantId(effects,
                List.of(new int[]{0, 1}, new int[]{2}));

        assertEquals(20, dominant);
    }

    @Test
    void dominantIdFallsBackWhenNoCellsAreGiven() {
        assertEquals(0, SymbolMana.dominantId(new int[]{20, 7}, List.of()));
    }
}
