package com.astune.gyromancy.element;

import com.astune.gyromancy.api.element.ElementConcentrations;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Accessor interface implemented on LevelChunk via mixin.
 * Provides direct access to the element override map for efficient processing.
 */
public interface IElementChunkAccessor {

    /** Returns the element override map for this chunk (never null). */
    Map<BlockPos, ElementConcentrations> gyromancy$getElementOverrides();

    /** Sets the element override map. */
    void gyromancy$setElementOverrides(Map<BlockPos, ElementConcentrations> overrides);

    /** Returns true if this chunk has any element overrides. */
    boolean gyromancy$hasElementOverrides();
}
