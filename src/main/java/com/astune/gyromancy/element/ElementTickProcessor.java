package com.astune.gyromancy.element;

import com.astune.gyromancy.Gyromancy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Drives element concentration processing each server tick.
 * Iterates all loaded chunks with overrides and delegates to {@link ElementChunkProcessor}.
 */
@EventBusSubscriber(modid = Gyromancy.MODID)
public final class ElementTickProcessor {

    private ElementTickProcessor() {}

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        int totalProcessed = 0;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            totalProcessed += processLevel(level);
        }

        if (totalProcessed > 0 && tickCounter % 200 == 0) {
            Gyromancy.LOGGER.debug("[Gyromancy] Element tick: {} chunks processed",
                    totalProcessed);
        }
    }

    private static int processLevel(ServerLevel level) {
        int processed = 0;
        for (ChunkPos chunkPos : ElementChunkEventHandler.getActiveChunkPositions(level.dimension())) {
            // Active chunks are tracked by load/unload events, so they should be loaded
            net.minecraft.world.level.chunk.ChunkAccess chunkAccess =
                    level.getChunkSource().getChunk(chunkPos.x, chunkPos.z,
                            net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
            if (!(chunkAccess instanceof LevelChunk chunk)) continue;
            if (chunk instanceof IElementChunkAccessor accessor && accessor.gyromancy$hasElementOverrides()) {
                ElementChunkProcessor.processChunk(chunk, level);
                processed++;
            }
        }
        return processed;
    }
}
