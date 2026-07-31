package com.astune.gyromancy.element;

import com.astune.gyromancy.Gyromancy;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks chunks with active element overrides, keyed by dimension+ChunkPos.
 * Registered explicitly via {@code NeoForge.EVENT_BUS} in {@code Gyromancy}.
 */
public final class ElementChunkEventHandler {

    private ElementChunkEventHandler() {}

    private static final Map<ResourceKey<Level>, Set<ChunkPos>> activeChunks = new ConcurrentHashMap<>();

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        if (chunk.getLevel() instanceof ServerLevel level) {
            ElementStorageManager.INSTANCE.onChunkLoaded(level, chunk);
        }
        if (chunk instanceof IElementChunkAccessor accessor && accessor.gyromancy$hasElementData()) {
            if (chunk.getLevel() instanceof ServerLevel sl) {
                activeChunks.computeIfAbsent(sl.dimension(), k -> ConcurrentHashMap.newKeySet())
                        .add(chunk.getPos());
                Gyromancy.LOGGER.debug("[Gyromancy] Chunk with overrides loaded: {}", chunk.getPos());
            }
        }
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        if (chunk.getLevel() instanceof ServerLevel sl) {
            Set<ChunkPos> set = activeChunks.get(sl.dimension());
            if (set != null) {
                set.remove(chunk.getPos());
            }
        }
    }

    public static void markActive(LevelChunk chunk) {
        if (!(chunk.getLevel() instanceof ServerLevel sl)) return;
        activeChunks.computeIfAbsent(sl.dimension(), k -> ConcurrentHashMap.newKeySet())
                .add(chunk.getPos());
        //Gyromancy.LOGGER.debug("[Gyromancy] Marked chunk active: {}", chunk.getPos());
    }

    public static void markInactive(LevelChunk chunk) {
        if (!(chunk.getLevel() instanceof ServerLevel sl)) return;
        Set<ChunkPos> set = activeChunks.get(sl.dimension());
        if (set != null) {
            set.remove(chunk.getPos());
        }
    }

    public static Set<ChunkPos> getActiveChunkPositions(ResourceKey<Level> dimension) {
        Set<ChunkPos> set = activeChunks.get(dimension);
        return set != null ? Collections.unmodifiableSet(set) : Collections.emptySet();
    }
}
