package com.astune.gyromancy.element;

import com.astune.gyromancy.Config;
import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.network.SyncDebugElementPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public final class ElementTickProcessor {

    private ElementTickProcessor() {}
    private static int tick = 0;

    public static void onServerTick(ServerTickEvent.Post event) {
        tick++;
        int total = 0;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            total += process(level);
        }
        //if (tick % 20 == 0)
            //Gyromancy.LOGGER.info("[Gyromancy] tick #{}: {} chunks", tick, total);

        // Full snapshot sync every 10 ticks — collect across ALL levels,
        // then send once so clients don't get overwritten by empty levels.
        if (tick % 10 == 0) {
            List<DebugChunk> debugChunks = new ArrayList<>();
            int cellCount = 0;

            for (ServerLevel level : event.getServer().getAllLevels()) {
                for (ChunkPos cp : ElementChunkEventHandler.getActiveChunkPositions(level.dimension())) {
                    LevelChunk chunk = level.getChunk(cp.x, cp.z);
                    if (chunk instanceof IElementChunkAccessor a && a.gyromancy$hasElementData()) {
                        ElementChunkData data = a.gyromancy$getElementData();
                        if (data == null) continue;
                        debugChunks.add(new DebugChunk(cp, data));
                        cellCount += data.activeCellCount();
                    }
                }
            }

            var poses = new ArrayList<BlockPos>(cellCount);
            long[] flatVals = new long[cellCount * ElementType.COUNT];
            long[] flatDerivs = new long[cellCount * ElementType.COUNT];
            int[] valueIndex = {0};
            for (DebugChunk debugChunk : debugChunks) {
                debugChunk.data().forEach(debugChunk.chunkPos(), (pos, concentrations) -> {
                    poses.add(pos);
                    for (long value : concentrations.values()) {
                        flatVals[valueIndex[0]] = value;
                        valueIndex[0]++;
                    }
                    int derivativeStart = valueIndex[0] - ElementType.COUNT;
                    for (int element = 0; element < ElementType.COUNT; element++) {
                        flatDerivs[derivativeStart + element] =
                                concentrations.derivatives()[element];
                    }
                });
            }
            PacketDistributor.sendToAllPlayers(
                    new SyncDebugElementPacket(poses, flatVals, flatDerivs));
        }
    }

    private static int process(ServerLevel level) {
        if(!Config.ENABLE_ELEMENT_TICK.get()) return 0;

        if (tick % 10 != 0) return 0;
        return ElementChunkProcessor.processLevel(
                level,
                ElementChunkEventHandler.getActiveChunkPositions(level.dimension()));
    }

    private record DebugChunk(ChunkPos chunkPos, ElementChunkData data) {}
}
