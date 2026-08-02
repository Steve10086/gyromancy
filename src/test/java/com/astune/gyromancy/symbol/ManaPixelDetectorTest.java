package com.astune.gyromancy.symbol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManaPixelDetectorTest {
    @Test
    void onlyManaWithoutGlyphOwnershipIsEligibleForFloodFill() {
        assertTrue(ManaPixelDetector.isUnclaimedMana(1, 0));
        assertFalse(ManaPixelDetector.isUnclaimedMana(0, 0));
        assertFalse(ManaPixelDetector.isUnclaimedMana(1, 7));
    }
}
