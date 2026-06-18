package com.astune.gyromancy.element;

import com.astune.gyromancy.api.element.ElementConcentrations;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.element.event.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.*;

/**
 * Processes element concentrations for a single chunk using a two-phase algorithm.
 *
 * <h3>Phase A — Snapshot & Diffuse</h3>
 * <ol>
 *   <li>Build a snapshot of all current override values (so diffusion reads are consistent).</li>
 *   <li>For each overridden position, gather its 8 neighbors' values from the snapshot
 *       (non-overridden neighbors → biome default).</li>
 *   <li>Compute the diffused value (average of self + 8 neighbors) and store in a pending map.</li>
 * </ol>
 *
 * <h3>Phase B — Decay, Recover, Cleanup, Events</h3>
 * <ol>
 *   <li>Apply decay (lose 10% of excess above default) and recovery (gain 10% of deficit below default).</li>
 *   <li>Clamp to [0, 1].</li>
 *   <li>Compute derivatives.</li>
 *   <li>Fire threshold/change events.</li>
 *   <li>Check cleanup: if all elements are close to default → remove position.</li>
 *   <li>Otherwise, update the override map with new values.</li>
 * </ol>
 */
public final class ElementChunkProcessor {

    private ElementChunkProcessor() {}

    /** Neighbor offsets for the 3×3 grid excluding self */
    private static final int[][] NEIGHBOR_OFFSETS = {
            {-1, -1}, {0, -1}, {1, -1},
            {-1,  0},          {1,  0},
            {-1,  1}, {0,  1}, {1,  1}
    };

    /**
     * Process all overridden positions in a single chunk for one tick.
     */
    public static void processChunk(LevelChunk chunk, ServerLevel level) {
        if (!(chunk instanceof IElementChunkAccessor accessor)) return;
        if (!accessor.gyromancy$hasElementOverrides()) return;

        Map<BlockPos, ElementConcentrations> overrides = accessor.gyromancy$getElementOverrides();
        if (overrides == null || overrides.isEmpty()) {
            accessor.gyromancy$setElementOverrides(null);
            ElementChunkEventHandler.markInactive(chunk);
            return;
        }

        // ── Phase A: Snapshot current state & compute diffused values ──
        Map<BlockPos, ElementConcentrations> snapshot = new HashMap<>(overrides);
        Map<BlockPos, ElementConcentrations> diffusedMap = new HashMap<>();

        for (Map.Entry<BlockPos, ElementConcentrations> entry : snapshot.entrySet()) {
            BlockPos pos = entry.getKey();
            ElementConcentrations current = entry.getValue();

            // Gather 8 neighbor values
            ElementConcentrations[] neighbors = new ElementConcentrations[8];
            for (int n = 0; n < 8; n++) {
                BlockPos neighborPos = pos.offset(NEIGHBOR_OFFSETS[n][0], 0, NEIGHBOR_OFFSETS[n][1]);
                // Look up in snapshot; if not overridden → null (use biome default in diffuse)
                neighbors[n] = snapshot.get(neighborPos);
            }

            ElementConcentrations biomeDefaults = ElementBiomeProvider.getDefault(level.getBiome(pos).value());
            ElementConcentrations diffused = current.diffuse(neighbors, biomeDefaults);
            diffusedMap.put(pos, diffused);
        }

        // ── Phase B: Decay, recover, events, cleanup ──
        Iterator<Map.Entry<BlockPos, ElementConcentrations>> it = overrides.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, ElementConcentrations> entry = it.next();
            BlockPos pos = entry.getKey();
            ElementConcentrations current = entry.getValue();
            ElementConcentrations diffused = diffusedMap.get(pos);
            if (diffused == null) continue; // should not happen

            ElementConcentrations biomeDefaults = ElementBiomeProvider.getDefault(level.getBiome(pos).value());

            // Apply decay/recovery
            ElementConcentrations next = diffused.decayExcessAndRecoverDeficit(biomeDefaults);

            // Fire events (compare old → new)
            checkAndFireEvents(level, pos, current, next);

            // Check cleanup
            if (next.isCloseToDefault(biomeDefaults)) {
                it.remove();
                ElementEventBus.fireCleanup(level, new ElementCleanedUpEvent(level, pos, next));
            } else {
                entry.setValue(next);
            }
        }

        // If all positions were cleaned up, clear the accessor
        if (overrides.isEmpty()) {
            accessor.gyromancy$setElementOverrides(null);
            ElementChunkEventHandler.markInactive(chunk);
        }
    }

    /**
     * Called when an override is written for the first time at a position.
     */
    public static void onFirstWrite(ServerLevel level, BlockPos pos, ElementConcentrations values) {
        ElementEventBus.fireActivation(level, new ElementActivatedEvent(level, pos, values));
    }

    // ── Internal ──

    private static void checkAndFireEvents(ServerLevel level, BlockPos pos,
                                           ElementConcentrations old, ElementConcentrations next) {
        for (ElementType type : ElementType.values()) {
            int i = type.ordinal();
            float oldVal = old.values()[i];
            float newVal = next.values()[i];
            float delta = newVal - oldVal;

            if (Math.abs(delta) < 0.0001f) continue;

            ElementThresholdEvent tEvent = new ElementThresholdEvent(
                    level, pos, type, oldVal, newVal, 0.3f,
                    delta > 0 ? ThresholdDirection.RISING_ABOVE : ThresholdDirection.FALLING_BELOW
            );
            ElementEventBus.checkAndFireThreshold(level, tEvent);

            ElementChangeEvent cEvent = new ElementChangeEvent(level, pos, type, delta, delta, oldVal, newVal);
            ElementEventBus.checkAndFireChange(level, cEvent);
        }
    }
}
