package com.astune.gyromancy.api.symbol;

import net.minecraft.resources.ResourceLocation;

/**
 * Result of matching a drawn symbol against a template.
 */
public record SymbolMatch(
        /** The matched symbol template ID */
        ResourceLocation symbolId,
        /** Confidence score from 0.0 to 1.0 */
        float confidence,
        /** Rotation angle in degrees relative to the template (0-360) */
        float rotationDegrees,
        /** Whether the drawn symbol is mirrored compared to the template */
        boolean mirrored,
        /** Scale factor relative to the template (1.0 = same size) */
        float scale,
        /** The bounding box center of this symbol on the canvas (normalized 0-1) */
        float centerX,
        float centerY,
        /** Classified role based on position and matching */
        SymbolRole role
) {
    /** Returns true if this match has sufficient confidence for activation */
    public boolean isConfident(float threshold) {
        return confidence >= threshold;
    }
}
