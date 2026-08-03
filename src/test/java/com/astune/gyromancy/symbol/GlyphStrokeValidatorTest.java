package com.astune.gyromancy.symbol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlyphStrokeValidatorTest {
    @Test
    void requiresManaAndExactGlyphAndSymbolOwnership() {
        assertTrue(GlyphStrokeValidator.isExpectedMarker(1, 7, 3, 7, 3));
        assertFalse(GlyphStrokeValidator.isExpectedMarker(0, 7, 3, 7, 3));
        assertFalse(GlyphStrokeValidator.isExpectedMarker(1, 8, 3, 7, 3));
        assertFalse(GlyphStrokeValidator.isExpectedMarker(1, 7, 4, 7, 3));
        assertTrue(GlyphStrokeValidator.isExpectedMarker(1, 256, 3, 256, 3));
        assertFalse(GlyphStrokeValidator.isExpectedMarker(1, -1, 3, 255, 3));
    }
}
