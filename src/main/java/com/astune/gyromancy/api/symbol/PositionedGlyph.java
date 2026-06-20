package com.astune.gyromancy.api.symbol;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * A successfully recognized glyph with its world position.
 * Stored in MagicArrayManager for later retrieval (e.g., by Phase 5 compilation).
 */
public record PositionedGlyph(
        int glyphId,
        ResourceLocation symbolId,
        float confidence,
        SymbolRole role,
        BlockPos worldPos,
        double minWorldX, double maxWorldX,
        double minWorldY, double maxWorldY
) {}
