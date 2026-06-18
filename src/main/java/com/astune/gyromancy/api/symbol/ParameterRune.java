package com.astune.gyromancy.api.symbol;

import net.minecraft.resources.ResourceLocation;

/**
 * Represents a parameter rune in a magic array. Parameter runes are placed between
 * the outer circle and center symbol, and determine effect parameters such as
 * strength, range, element type, duration, etc.
 */
public record ParameterRune(
        /** The rune's type identifier */
        ResourceLocation runeId,
        /** The value/level of this rune (depends on rune type) */
        float value,
        /** Additional metadata string for extensible use */
        String metadata
) {
    /** Rune categories for type-checking during compilation */
    public enum RuneCategory {
        MAGNITUDE,      // Numeric magnitude (strength, count, etc.)
        ELEMENT,        // Element type reference
        DIRECTION,      // Spatial direction
        DURATION,       // Time duration
        AREA,           // Spatial area
        MODIFIER        // Effect modifier/qualifier
    }

    public static ParameterRune of(ResourceLocation id, float value) {
        return new ParameterRune(id, value, "");
    }
}
