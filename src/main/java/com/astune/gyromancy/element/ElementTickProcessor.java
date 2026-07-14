package com.astune.gyromancy.element;

import com.astune.gyromancy.Config;
import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.network.SyncDebugElementPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;

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
            var poses = new ArrayList<BlockPos>();
            var vals = new ArrayList<Long>();
            var derivs = new ArrayList<Long>();

            for (ServerLevel level : event.getServer().getAllLevels()) {
                for (ChunkPos cp : ElementChunkEventHandler.getActiveChunkPositions(level.dimension())) {
                    LevelChunk chunk = level.getChunk(cp.x, cp.z);
                    if (chunk instanceof IElementChunkAccessor a && a.gyromancy$hasElementOverrides()) {
                        for (var e : a.gyromancy$getElementOverrides().entrySet()) {
                            poses.add(e.getKey());
                            for (long v : e.getValue().values()) vals.add(v);
                            for (long d : e.getValue().derivatives()) derivs.add(d);
                        }
                    }
                }
            }

            long[] flatVals = new long[vals.size()];
            long[] flatDerivs = new long[derivs.size()];
            for (int i = 0; i < vals.size(); i++) flatVals[i] = vals.get(i);
            for (int i = 0; i < derivs.size(); i++) flatDerivs[i] = derivs.get(i);
            PacketDistributor.sendToAllPlayers(
                    new SyncDebugElementPacket(poses, flatVals, flatDerivs));
        }
    }

    private static int process(ServerLevel level) {
        if(!Config.ENABLE_ELEMENT_TICK.get()) return 0;

        int n = 0;
        if (tick % 10 == 0) {
            for (ChunkPos cp : ElementChunkEventHandler.getActiveChunkPositions(level.dimension())) {
                LevelChunk chunk = level.getChunk(cp.x, cp.z);
                if (chunk instanceof IElementChunkAccessor a && a.gyromancy$hasElementOverrides()) {
                    ElementChunkProcessor.processChunk(chunk, level);
                    n++;
                }
            }
        }
        return n;
    }
}
