package com.astune.gyromancy.element;

import com.astune.gyromancy.Config;
import com.astune.gyromancy.api.element.ElementConcentrations;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.element.event.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.*;

/**
 * Each tick per tracked position: decay → share excess/27 with self + 26 neighbors.
 * Total energy conserved. No separate push/afterPush/diffuse phases.
 */
public final class ElementChunkProcessor {

    private ElementChunkProcessor() {}

    private static final java.util.function.BiFunction<long[], long[], long[]> ADD =
            (a, b) -> { for (int i = 0; i < a.length; i++) a[i] += b[i]; return a; };

    private static final int[][] OFF;
    static {
        OFF = new int[26][];
        int idx = 0;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++)
                    if (dx != 0 || dy != 0 || dz != 0)
                        OFF[idx++] = new int[]{dx, dy, dz};
    }

    public static void processChunk(LevelChunk chunk, ServerLevel level) {
        if (!(chunk instanceof IElementChunkAccessor accessor)) return;
        if (!accessor.gyromancy$hasElementOverrides()) return;

        Map<BlockPos, ElementConcentrations> overrides = accessor.gyromancy$getElementOverrides();
        if (overrides.isEmpty()) { accessor.gyromancy$setElementOverrides(null); ElementChunkEventHandler.markInactive(chunk); return; }

        // ── Step 1: decay all tracked positions from snapshot ──
        Map<BlockPos, ElementConcentrations> decred = new HashMap<>();
        for (var e : overrides.entrySet()) {
            BlockPos p = e.getKey();
            ElementConcentrations def = ElementBiomeProvider.getDefault(level.getBiome(p).value());
            ElementConcentrations dec = e.getValue().decayAndRecover(def);
            fireEvents(level, p, e.getValue(), dec);
            if (!dec.isCloseToDefault(def)) decred.put(p, dec);
            else ElementEventBus.fireCleanup(level, new ElementCleanedUpEvent(level, p, dec));
        }
        if (decred.isEmpty()) {
            accessor.gyromancy$setElementOverrides(null);
            ElementChunkEventHandler.markInactive(chunk);
            return;
        }
        if (!Config.ENABLE_ELEMENT_DIFFUSION.get()) {
            accessor.gyromancy$setElementOverrides(decred);
            ElementChunkEventHandler.markActive(chunk);
            return;
        }

        // ── Step 2: classify positions ──
        Map<BlockPos, ElementConcentrations> sharers = new HashMap<>();
        Map<BlockPos, ElementConcentrations> carries = new HashMap<>();
        for (var e : decred.entrySet()) {
            BlockPos p = e.getKey();
            ElementConcentrations def = ElementBiomeProvider.getDefault(level.getBiome(p).value());
            long[] share = e.getValue().excessShare(def);
            boolean hasShare = false;
            for (long s : share) { if (s >= 2) { hasShare = true; break; } }
            if (hasShare) sharers.put(p, e.getValue());
            else carries.put(p, e.getValue());
        }

        if (sharers.isEmpty()) {
            Map<BlockPos, ElementConcentrations> finalResult = new HashMap<>();
            for (var e : carries.entrySet()) {
                ElementConcentrations def = ElementBiomeProvider.getDefault(level.getBiome(e.getKey()).value());
                if (!e.getValue().isCloseToDefault(def)) finalResult.put(e.getKey(), e.getValue());
            }
            accessor.gyromancy$setElementOverrides(finalResult.isEmpty() ? null : finalResult);
            if (finalResult.isEmpty()) ElementChunkEventHandler.markInactive(chunk);
            else ElementChunkEventHandler.markActive(chunk);
            return;
        }

        // ── Step 3: share excess/27 from strong positions to self + 26 neighbors ──
        Map<BlockPos, long[]> acc = distributeShares(sharers, level);

        // ── Step 4: reconstruct each position from its base value + accumulated shares ──
        Map<BlockPos, ElementConcentrations> result = new HashMap<>();
        for (var e : acc.entrySet()) {
            BlockPos p = e.getKey();
            ElementConcentrations defL = ElementBiomeProvider.getDefault(level.getBiome(p).value());
            ElementConcentrations carryVal = carries.get(p);
            ElementConcentrations sharerVal = carryVal != null ? null : sharers.get(p);
            long[] base = new long[ElementType.COUNT];
            if (carryVal != null) {
                System.arraycopy(carryVal.values(), 0, base, 0, ElementType.COUNT);
            } else if (sharerVal != null) {
                long[] selfShare = sharerVal.excessShare(defL);
                for (int i = 0; i < ElementType.COUNT; i++)
                    base[i] = selfShare[i] > 0 ? defL.values()[i] : sharerVal.values()[i];
            } else {
                System.arraycopy(defL.values(), 0, base, 0, ElementType.COUNT);
            }
            long[] vals = new long[ElementType.COUNT];
            for (int i = 0; i < ElementType.COUNT; i++)
                vals[i] = Math.max(ElementConcentrations.MIN_VALUE, Math.min(base[i] + e.getValue()[i], ElementConcentrations.MAX_VALUE));
            result.put(p, new ElementConcentrations(vals, new long[ElementType.COUNT]));
        }

        // Carries that received no shares: keep their decayed value as-is
        for (var e : carries.entrySet())
            result.putIfAbsent(e.getKey(), e.getValue());

        persistByChunk(level, chunk.getPos(), result, acc);
    }

    /**
     * Writes {@code result} to the correct chunks — positions that belong to
     * other chunks (cross-boundary spill from {@link #distributeShares}) are
     * merged into those chunks rather than polluting the current one.
     */
    private static void persistByChunk(ServerLevel level, ChunkPos currentPos,
            Map<BlockPos, ElementConcentrations> result,
            Map<BlockPos, long[]> acc) {

        Map<ChunkPos, Map<BlockPos, ElementConcentrations>> byChunk = new HashMap<>();
        for (var e : result.entrySet()) {
            ChunkPos cp = new ChunkPos(e.getKey());
            byChunk.computeIfAbsent(cp, k -> new HashMap<>()).put(e.getKey(), e.getValue());
        }

        // ── Current chunk: use reconstructed values directly ──
        Map<BlockPos, ElementConcentrations> current = byChunk.getOrDefault(currentPos, new HashMap<>());
        LevelChunk currentChunk = level.getChunk(currentPos.x, currentPos.z);
        if (currentChunk instanceof IElementChunkAccessor a) {
            a.gyromancy$setElementOverrides(current.isEmpty() ? null : current);
            if (current.isEmpty()) ElementChunkEventHandler.markInactive(currentChunk);
            else ElementChunkEventHandler.markActive(currentChunk);
        }

        // ── Adjacent chunks: merge acc shares into existing overrides ──
        for (var entry : byChunk.entrySet()) {
            if (entry.getKey().equals(currentPos)) continue;
            ChunkPos cp = entry.getKey();
            LevelChunk target = level.getChunk(cp.x, cp.z);
            if (!(target instanceof IElementChunkAccessor ta)) continue;

            Map<BlockPos, ElementConcentrations> targetOverrides = ta.gyromancy$getElementOverrides();
            for (var posEntry : entry.getValue().entrySet()) {
                BlockPos pos = posEntry.getKey();
                ElementConcentrations def = ElementBiomeProvider.getDefault(level.getBiome(pos).value());
                ElementConcentrations existing = targetOverrides.get(pos);
                long[] shares = acc.get(pos);
                long[] baseVals = existing != null ? existing.values() : def.values();
                long[] vals = new long[ElementType.COUNT];
                for (int i = 0; i < ElementType.COUNT; i++)
                    vals[i] = Math.max(ElementConcentrations.MIN_VALUE,
                            Math.min(baseVals[i] + (shares != null ? shares[i] : 0),
                                    ElementConcentrations.MAX_VALUE));
                targetOverrides.put(pos, new ElementConcentrations(vals, new long[ElementType.COUNT]));
            }
            ta.gyromancy$setElementOverrides(targetOverrides.isEmpty() ? null : targetOverrides);
            if (!targetOverrides.isEmpty()) ElementChunkEventHandler.markActive(target);
        }
    }

    /**
     * Distributes excess/27 from each sharer to itself and its 26 immediate neighbors.
     * Each position in the returned map owns an independent {@code long[]} — no aliasing.
     */
    private static Map<BlockPos, long[]> distributeShares(
            Map<BlockPos, ElementConcentrations> sharers, ServerLevel level) {
        Map<BlockPos, long[]> acc = new HashMap<>();
        for (var e : sharers.entrySet()) {
            BlockPos p = e.getKey();
            ElementConcentrations def = ElementBiomeProvider.getDefault(level.getBiome(p).value());
            long[] share = e.getValue().excessShare(def);

            acc.compute(p, (k, v) -> v == null ? share.clone() : ADD.apply(v, share));
            for (int[] o : OFF)
                acc.compute(p.offset(o[0], o[1], o[2]), (k, v) -> v == null ? share.clone() : ADD.apply(v, share));
        }
        return acc;
    }

    private static void fireEvents(ServerLevel level, BlockPos pos, ElementConcentrations old, ElementConcentrations next) {
        for (ElementType t : ElementType.values()) {
            int i = t.ordinal();
            long d = next.values()[i] - old.values()[i];
            if (d == 0) continue;
            ElementEventBus.checkAndFireThreshold(level, new ElementThresholdEvent(level, pos, t, old.values()[i], next.values()[i], 300L,
                    d > 0 ? ThresholdDirection.RISING_ABOVE : ThresholdDirection.FALLING_BELOW));
            ElementEventBus.checkAndFireChange(level, new ElementChangeEvent(level, pos, t, d, d, old.values()[i], next.values()[i]));
        }
    }

    public static void onFirstWrite(ServerLevel level, BlockPos pos, ElementConcentrations values) {
        ElementEventBus.fireActivation(level, new ElementActivatedEvent(level, pos, values));
    }
}
