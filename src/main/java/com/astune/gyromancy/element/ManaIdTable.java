package com.astune.gyromancy.element;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.ink.InkRegistry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Fixed mana-id table. A mana id is the integer stored in a mana effect layer;
 * each id resolves to a nine-slot int row used to solve an array's elements.
 * Unknown ids fall back to {@link #FALLBACK_ID} and are reported once.
 */
public final class ManaIdTable {
    public static final int FALLBACK_ID = 0;

    private static final Map<Integer, int[]> ROWS = new HashMap<>();
    private static final Set<Integer> WARNED = new HashSet<>();

    static {
        // Rows follow ElementType order: wind, fire, water, earth, light, dark, space, time, mana.
        register(FALLBACK_ID, 1, 1, 1, 1, 1, 1, 1, 1, 1);
        register(InkRegistry.MANA_INK_ID, 0, 0, 0, 0, 0, 0, 0, 0, 1);
        register(InkRegistry.WATER_INK_ID, 0, 0, 1, 0, 0, 0, 0, 0, 0);
        register(InkRegistry.FIRE_INK_ID, 0, 1, 0, 0, 0, 0, 0, 0, 0);
        register(InkRegistry.EARTH_INK_ID, 0, 0, 0, 1, 0, 0, 0, 0, 0);
        register(InkRegistry.WIND_INK_ID, 1, 0, 0, 0, 0, 0, 0, 0, 0);
        register(InkRegistry.WATER_MANA_INK_ID, 0, 0, 1, 0, 0, 0, 0, 0, 1);
        register(InkRegistry.FIRE_MANA_INK_ID, 0, 1, 0, 0, 0, 0, 0, 0, 1);
        register(InkRegistry.EARTH_MANA_INK_ID, 0, 0, 0, 1, 0, 0, 0, 0, 1);
        register(InkRegistry.WIND_MANA_INK_ID, 1, 0, 0, 0, 0, 0, 0, 0, 1);
        register(InkRegistry.LIGHT_MANA_INK_ID, 0, 0, 0, 0, 1, 0, 0, 0, 1);
        register(InkRegistry.DARK_MANA_INK_ID, 0, 0, 0, 0, 0, 1, 0, 0, 1);
    }

    private ManaIdTable() {}

    /** Returns the fixed row for this id, or the fallback row plus a warning. */
    public static int[] rowFor(int id) {
        int[] row = ROWS.get(id);
        if (row != null) return row;
        if (WARNED.add(id)) {
            Gyromancy.LOGGER.warn("[ManaId] Unknown mana id {}; falling back to id {}",
                    id, FALLBACK_ID);
        }
        return ROWS.get(FALLBACK_ID);
    }

    /**
     * Replaces the row for an id with {@link ElementType#COUNT} values in
     * {@link ElementType} order. Mostly useful for tests and future content.
     */
    public static void register(int id, int... row) {
        if (row == null || row.length != ElementType.COUNT) {
            throw new IllegalArgumentException("A mana id row needs "
                    + ElementType.COUNT + " slots");
        }
        ROWS.put(id, row.clone());
    }

}
