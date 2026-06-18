package com.astune.gyromancy.element;

import com.astune.gyromancy.Gyromancy;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Binds element processing to the chunk lifecycle.
 * Tracks which chunks have active element overrides, keyed by dimension + ChunkPos.
 */
@EventBusSubscriber(modid = Gyromancy.MODID)
public final class ElementChunkEventHandler {

    private ElementChunkEventHandler() {}

    /** Active chunks with overrides: dimension → set of chunk positions */
    private static final Map<ResourceKey<Level>, Set<ChunkPos>> activeChunks = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        if (chunk instanceof IElementChunkAccessor accessor && accessor.gyromancy$hasElementOverrides()) {
            if (chunk.getLevel() instanceof ServerLevel sl) {
                activeChunks.computeIfAbsent(sl.dimension(), k -> ConcurrentHashMap.newKeySet())
                        .add(chunk.getPos());
            }
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        if (chunk.getLevel() instanceof ServerLevel sl) {
            Set<ChunkPos> set = activeChunks.get(sl.dimension());
            if (set != null) {
                set.remove(chunk.getPos());
            }
        }
    }

    /** Register a chunk with overrides for processing. Called when first override is written. */
    public static void markActive(LevelChunk chunk) {
        if (!(chunk.getLevel() instanceof ServerLevel sl)) return;
        activeChunks.computeIfAbsent(sl.dimension(), k -> ConcurrentHashMap.newKeySet())
                .add(chunk.getPos());
    }

    /** Deregister a chunk when all overrides are cleaned up. */
    public static void markInactive(LevelChunk chunk) {
        if (!(chunk.getLevel() instanceof ServerLevel sl)) return;
        Set<ChunkPos> set = activeChunks.get(sl.dimension());
        if (set != null) {
            set.remove(chunk.getPos());
        }
    }

    /** Returns an unmodifiable set of chunk positions with overrides in the given dimension. */
    public static Set<ChunkPos> getActiveChunkPositions(ResourceKey<Level> dimension) {
        Set<ChunkPos> set = activeChunks.get(dimension);
        return set != null ? Collections.unmodifiableSet(set) : Collections.emptySet();
    }
}
