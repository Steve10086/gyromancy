package com.astune.gyromancy.element;

import com.astune.gyromancy.api.element.ElementConcentrations;
import com.astune.gyromancy.api.element.IElementStorage;
import com.astune.gyromancy.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages element concentration storage using NeoForge's AttachmentType on LevelChunk.
 *
 * <p>Implements lazy initialization: unmodified positions return biome defaults without storing.
 * Only positions explicitly modified via {@link #set} create storage entries.
 * Entries are automatically cleaned up when all values regress to biome defaults.</p>
 */
public final class ElementStorageManager implements IElementStorage {

    public static final ElementStorageManager INSTANCE = new ElementStorageManager();

    private ElementStorageManager() {}

    @Override
    public ElementConcentrations get(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return getBiomeDefault(level, pos);
        }
        LevelChunk chunk = level.getChunkAt(pos);
        if (!(chunk instanceof IElementChunkAccessor accessor)) {
            return getBiomeDefault(level, pos);
        }
        Map<BlockPos, ElementConcentrations> overrides = accessor.gyromancy$getElementOverrides();
        if (overrides != null) {
            ElementConcentrations conc = overrides.get(pos);
            if (conc != null) return conc;
        }
        return getBiomeDefault(level, pos);
    }

    @Override
    public void set(Level level, BlockPos pos, ElementConcentrations values) {
        LevelChunk chunk = level.getChunkAt(pos);
        if (!(chunk instanceof IElementChunkAccessor accessor)) return;

        Map<BlockPos, ElementConcentrations> overrides = accessor.gyromancy$getElementOverrides();
        boolean isNew = (overrides == null || !overrides.containsKey(pos));

        if (overrides == null) {
            overrides = new HashMap<>();
            accessor.gyromancy$setElementOverrides(overrides);
        }

        overrides.put(pos, values);
        chunk.setUnsaved(true);
        ElementChunkEventHandler.markActive(chunk);

        // Fire activation event on first write
        if (isNew && level instanceof ServerLevel sl) {
            ElementChunkProcessor.onFirstWrite(sl, pos, values);
        }
    }

    @Override
    public boolean remove(Level level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        if (!(chunk instanceof IElementChunkAccessor accessor)) return false;
        Map<BlockPos, ElementConcentrations> overrides = accessor.gyromancy$getElementOverrides();
        if (overrides == null) return false;
        boolean removed = overrides.remove(pos) != null;
        if (overrides.isEmpty()) {
            accessor.gyromancy$setElementOverrides(null);
        }
        if (removed) chunk.setUnsaved(true);
        return removed;
    }

    @Override
    public boolean isOverridden(Level level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        if (!(chunk instanceof IElementChunkAccessor accessor)) return false;
        Map<BlockPos, ElementConcentrations> overrides = accessor.gyromancy$getElementOverrides();
        return overrides != null && overrides.containsKey(pos);
    }

    private static ElementConcentrations getBiomeDefault(Level level, BlockPos pos) {
        var biome = level.getBiome(pos);
        return ElementBiomeProvider.getDefault(biome.value());
    }
}
