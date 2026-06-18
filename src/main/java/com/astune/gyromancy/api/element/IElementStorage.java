package com.astune.gyromancy.api.element;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Abstraction over element concentration storage, allowing different backend implementations.
 * The default implementation uses NeoForge's AttachmentType on LevelChunk for per-chunk override storage.
 */
public interface IElementStorage {

    /**
     * Reads the element concentrations at the given position.
     * If no override exists, returns the biome-default concentrations directly (without storing).
     *
     * @param level the world
     * @param pos   the position
     * @return the element concentrations at this position (never null)
     */
    ElementConcentrations get(Level level, BlockPos pos);

    /**
     * Explicitly modifies the element concentrations at a position.
     * Creates override storage if it doesn't already exist.
     *
     * @param level  the world
     * @param pos    the position
     * @param values the new concentration values
     */
    void set(Level level, BlockPos pos, ElementConcentrations values);

    /**
     * Removes the override for a position if all values have returned to biome defaults.
     * After removal, subsequent reads will return biome defaults.
     *
     * @param level the world
     * @param pos   the position
     * @return true if the override was removed, false if it didn't exist
     */
    boolean remove(Level level, BlockPos pos);

    /**
     * Checks whether this position has an explicit override stored.
     *
     * @param level the world
     * @param pos   the position
     * @return true if an override exists (i.e., the position has been explicitly modified)
     */
    boolean isOverridden(Level level, BlockPos pos);
}
