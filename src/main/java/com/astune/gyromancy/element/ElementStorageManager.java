package com.astune.gyromancy.element;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementConcentrations;
import com.astune.gyromancy.api.element.IElementStorage;
import com.astune.gyromancy.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Map;

public final class ElementStorageManager implements IElementStorage {

    public static final ElementStorageManager INSTANCE = new ElementStorageManager();
    private ElementStorageManager() {}

    @Override
    public ElementConcentrations get(Level level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        if (!(chunk instanceof IElementChunkAccessor accessor))
            return ElementBiomeProvider.getDefault(level.getBiome(pos).value());
        if (!accessor.gyromancy$hasElementOverrides())
            return ElementBiomeProvider.getDefault(level.getBiome(pos).value());
        Map<BlockPos, ElementConcentrations> m = accessor.gyromancy$getElementOverrides();
        ElementConcentrations c = m.get(pos);
        return c != null ? c : ElementBiomeProvider.getDefault(level.getBiome(pos).value());
    }

    @Override
    public void set(Level level, BlockPos pos, ElementConcentrations values) {
        LevelChunk chunk = level.getChunkAt(pos);
        if (!(chunk instanceof IElementChunkAccessor accessor)) return;

        Map<BlockPos, ElementConcentrations> m = accessor.gyromancy$getElementOverrides();
        boolean isNew = !m.containsKey(pos);
        m.put(pos, values);
        accessor.gyromancy$setElementOverrides(m);
        ElementChunkEventHandler.markActive(chunk);

        if (isNew && level instanceof ServerLevel sl)
            ElementChunkProcessor.onFirstWrite(sl, pos, values);
    }

    @Override
    public boolean remove(Level level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        if (!(chunk instanceof IElementChunkAccessor accessor)) return false;
        if (!accessor.gyromancy$hasElementOverrides()) return false;
        Map<BlockPos, ElementConcentrations> m = accessor.gyromancy$getElementOverrides();
        boolean r = m.remove(pos) != null;
        accessor.gyromancy$setElementOverrides(m.isEmpty() ? null : m);
        return r;
    }

    @Override
    public boolean isOverridden(Level level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        if (!(chunk instanceof IElementChunkAccessor accessor)) return false;
        if (!accessor.gyromancy$hasElementOverrides()) return false;
        return accessor.gyromancy$getElementOverrides().containsKey(pos);
    }
}
