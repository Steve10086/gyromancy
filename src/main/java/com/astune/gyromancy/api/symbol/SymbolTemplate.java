package com.astune.gyromancy.api.symbol;

import net.minecraft.resources.ResourceLocation;

/**
 * Defines the canonical pattern for a symbol used in magic arrays.
 * Symbols are drawn by players and recognized via geometric pattern matching.
 */
public record SymbolTemplate(
        /** Unique identifier for this symbol */
        ResourceLocation id,
        /** Normalized binary pixel grid (e.g., 32×32). 1 = drawn pixel, 0 = empty */
        int[][] pattern,
        /** Number of characteristic feature points */
        int featurePoints,
        /** Whether rotation produces a valid variant of this symbol */
        boolean allowRotation,
        /** Whether mirroring produces a valid (different) variant */
        boolean allowMirror,
        /** The role this symbol typically plays in a magic array */
        SymbolRole defaultRole
) {
    public int getWidth() {
        return pattern.length > 0 ? pattern[0].length : 0;
    }

    public int getHeight() {
        return pattern.length;
    }

    public enum SymbolRole {
        /** The outermost boundary circle */
        OUTER_CIRCLE,
        /** The central function-defining symbol */
        CENTER_SYMBOL,
        /** A parameter-modifying rune */
        PARAMETER_RUNE,
        /** Unknown/unclassified */
        UNKNOWN
    }
}
