package com.astune.gyromancy.api.array;

import com.astune.gyromancy.api.symbol.SymbolMatch;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/**
 * Represents the recognized components of a magic array on a canvas surface.
 * A complete array consists of an outer circle, a center symbol (determines function),
 * and parameter runes (determine effect parameters).
 */
public record MagicArrayComponents(
        /** The canvas face position where the outer circle was detected */
        BlockPos canvasPos,
        /** UUID of the recognized outer circle symbol */
        SymbolMatch outerCircle,
        /** UUID of the recognized center/function symbol */
        SymbolMatch centerSymbol,
        /** Recognized parameter runes between the circle and center */
        List<SymbolMatch> parameterRunes,
        /** The world the array exists in */
        Level level
) {
    /**
     * Returns true if this array has all required components with sufficient confidence.
     */
    public boolean isValid(float minConfidence) {
        return outerCircle != null && outerCircle.confidence() >= minConfidence
                && centerSymbol != null && centerSymbol.confidence() >= minConfidence
                && parameterRunes != null;
    }
}
