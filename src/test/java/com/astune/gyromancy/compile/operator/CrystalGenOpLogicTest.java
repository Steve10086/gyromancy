package com.astune.gyromancy.compile.operator;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrystalGenOpLogicTest {
    private static final long THRESHOLD = CrystalGenOp.ELEMENT_THRESHOLD;
    private static final int INTERVAL = CrystalGenOp.SCAN_INTERVAL;

    @Test
    void addsQualifyingPositionsWithTimerEqualToInterval() {
        Map<BlockPos, Long> tracked = new HashMap<>();
        BlockPos a = new BlockPos(1, 2, 3);
        BlockPos b = new BlockPos(4, 5, 6);
        long levelA = THRESHOLD + 5_000L;
        long levelB = THRESHOLD + 1_000L;

        long total = CrystalGenOp.updateTracked(tracked, List.of(a, b),
                pos -> pos.equals(a) ? levelA : levelB,
                pos -> true, THRESHOLD, INTERVAL);

        assertEquals(2, tracked.size());
        assertEquals(INTERVAL, tracked.get(a.immutable()));
        assertEquals(levelA + levelB, total);
    }

    @Test
    void advancesExistingTimersByIntervalInsteadOfOne() {
        Map<BlockPos, Long> tracked = new HashMap<>();
        BlockPos a = new BlockPos(1, 2, 3);
        tracked.put(a.immutable(), 5L);

        long total = CrystalGenOp.updateTracked(tracked, List.of(a),
                pos -> THRESHOLD + 5_000L, pos -> true, THRESHOLD, INTERVAL);

        assertEquals(15L, tracked.get(a.immutable()));
        assertEquals(THRESHOLD + 5_000L, total);
    }

    @Test
    void evictsPositionsBelowTheElementThreshold() {
        Map<BlockPos, Long> tracked = new HashMap<>();
        BlockPos hot = new BlockPos(1, 2, 3);
        BlockPos weak = new BlockPos(4, 5, 6);
        tracked.put(weak.immutable(), 5L);

        long total = CrystalGenOp.updateTracked(tracked, List.of(hot, weak),
                pos -> pos.equals(hot) ? THRESHOLD + 12_000L : THRESHOLD - 1L,
                pos -> true, THRESHOLD, INTERVAL);

        assertEquals(1, tracked.size());
        assertEquals(hot.immutable(), tracked.keySet().iterator().next());
        assertEquals(THRESHOLD + 12_000L, total);
    }

    @Test
    void evictsPositionsThatAreNoLongerAir() {
        Map<BlockPos, Long> tracked = new HashMap<>();
        BlockPos air = new BlockPos(1, 2, 3);
        BlockPos occupied = new BlockPos(4, 5, 6);

        long total = CrystalGenOp.updateTracked(tracked, List.of(air, occupied),
                pos -> THRESHOLD + 12_000L,
                pos -> pos.equals(air), THRESHOLD, INTERVAL);

        assertEquals(1, tracked.size());
        assertEquals(air.immutable(), tracked.keySet().iterator().next());
        assertEquals(THRESHOLD + 12_000L, total);
    }

    @Test
    void evictsPositionsTheEffectMovedAwayFrom() {
        Map<BlockPos, Long> tracked = new HashMap<>();
        BlockPos inside = new BlockPos(1, 2, 3);
        BlockPos left = new BlockPos(4, 5, 6);
        tracked.put(inside.immutable(), 5L);
        tracked.put(left.immutable(), 5L);
        long level = THRESHOLD + 12_000L;

        long total = CrystalGenOp.updateTracked(tracked, List.of(inside),
                pos -> level, pos -> true, THRESHOLD, INTERVAL);

        assertEquals(1, tracked.size());
        assertTrue(tracked.containsKey(inside.immutable()));
        assertEquals(level, total);
    }

    @Test
    void capsTrackedSizeWhenTheEffectCoversTooManyPositions() {
        Map<BlockPos, Long> tracked = new HashMap<>();
        List<BlockPos> inside = new ArrayList<>(1_000);
        for (int i = 0; i < 1_000; i++) inside.add(new BlockPos(i, 0, 0));
        long level = THRESHOLD + 12_000L;

        long total = CrystalGenOp.updateTracked(tracked, inside,
                pos -> level, pos -> true, THRESHOLD, INTERVAL);

        assertTrue(tracked.size() <= 512, "tracked map must remain capped");
        assertEquals(tracked.size() * level, total);
    }
}