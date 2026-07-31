package com.astune.gyromancy.element;

/**
 * Accessor interface implemented on LevelChunk via mixin.
 * Provides direct access to dense element tile data.
 */
public interface IElementChunkAccessor {

    /** Returns existing tile data, or null when the chunk is inactive. */
    ElementChunkData gyromancy$getElementData();

    /** Returns tile data, creating the attachment when necessary. */
    ElementChunkData gyromancy$getOrCreateElementData();

    /** Removes all element tile data from this chunk. */
    void gyromancy$clearElementData();

    /** Marks the chunk attachment dirty after an in-place tile mutation. */
    void gyromancy$markElementDataDirty();

    /** Returns true if this chunk has non-empty element tile data. */
    boolean gyromancy$hasElementData();
}
