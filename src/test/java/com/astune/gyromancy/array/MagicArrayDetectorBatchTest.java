package com.astune.gyromancy.array;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicArrayDetectorBatchTest {
    @Test
    void batchRegistrationPlacesEveryRuneBeforeItsCircles() {
        List<String> eventOrder = List.of("outer-a", "fire", "outer-b", "arrow");

        assertEquals(
                List.of("fire", "arrow", "outer-a", "outer-b"),
                MagicArrayDetector.circlesLast(eventOrder, value -> value.startsWith("outer")));
    }

    @Test
    void glyphInvalidationTracksManaPresenceInsteadOfMarkerMetadata() {
        assertTrue(MagicArrayDetector.manaPresenceChanged(1, 0));
        assertTrue(MagicArrayDetector.manaPresenceChanged(0, 1));
        assertFalse(MagicArrayDetector.manaPresenceChanged(1, 255));
        assertFalse(MagicArrayDetector.manaPresenceChanged(0, 0));
    }
}
