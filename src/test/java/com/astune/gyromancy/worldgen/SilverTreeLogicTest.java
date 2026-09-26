package com.astune.gyromancy.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SilverTreeLogicTest {

    private static Set<BlockPos> trunk(int height) {
        Set<BlockPos> logs = new HashSet<>();
        for (int y = 0; y < height; y++) {
            logs.add(new BlockPos(0, y, 0));
        }
        return logs;
    }

    @Test
    void collectsTheWholeConnectedComponentInBreadthFirstOrder() {
        Set<BlockPos> component = new HashSet<>();
        for (int y = 0; y < 12; y++) {
            component.add(new BlockPos(0, y, 0));
        }
        component.add(new BlockPos(1, 5, 0));
        component.add(new BlockPos(2, 5, 0));
        Set<BlockPos> logs = new HashSet<>(component);
        logs.add(new BlockPos(9, 9, 9));

        List<BlockPos> order = SilverTreeLogic.collectConnectedLogs(
                new BlockPos(0, 5, 0), logs::contains, SilverTreeLogic.MAX_CONNECTED_LOGS);

        assertEquals(14, order.size());
        assertEquals(new BlockPos(0, 5, 0), order.get(0));
        assertTrue(order.containsAll(component));
        assertTrue(!order.contains(new BlockPos(9, 9, 9)),
                "the isolated log is outside the connected component");
    }

    @Test
    void respectsTheLogLimit() {
        Set<BlockPos> logs = trunk(100);

        List<BlockPos> order = SilverTreeLogic.collectConnectedLogs(
                new BlockPos(0, 0, 0), logs::contains, SilverTreeLogic.MIN_TREE_LOGS);

        assertEquals(SilverTreeLogic.MIN_TREE_LOGS, order.size());
    }

    @Test
    void returnsNothingWhenTheOriginIsNotALog() {
        Set<BlockPos> logs = trunk(10);

        List<BlockPos> order = SilverTreeLogic.collectConnectedLogs(
                new BlockPos(5, 5, 5), logs::contains, SilverTreeLogic.MAX_CONNECTED_LOGS);

        assertTrue(order.isEmpty());
    }

    @Test
    void firstQualifyingKeepsTheWalkOrder() {
        List<BlockPos> order = List.of(
                new BlockPos(0, 0, 0), new BlockPos(0, 1, 0), new BlockPos(0, 2, 0));

        Optional<BlockPos> found = SilverTreeLogic.firstQualifying(order, pos -> pos.getY() == 1);

        assertEquals(Optional.of(new BlockPos(0, 1, 0)), found);
    }

    @Test
    void growthIsRejectedForTreesWithFewerThanNineLogs() {
        Set<BlockPos> logs = trunk(SilverTreeLogic.MIN_TREE_LOGS - 1);

        Optional<BlockPos> target = SilverTreeGrowth.findGrowthTarget(
                new BlockPos(0, 0, 0), logs::contains, pos -> true, pos -> true);

        assertTrue(target.isEmpty());
    }

    @Test
    void growthPicksTheFirstQualifyingLogOfALargeEnoughTree() {
        Set<BlockPos> logs = trunk(SilverTreeLogic.MIN_TREE_LOGS + 2);

        Optional<BlockPos> target = SilverTreeGrowth.findGrowthTarget(
                new BlockPos(0, 0, 0),
                logs::contains,
                pos -> pos.getY() == SilverTreeLogic.MIN_TREE_LOGS + 1,
                pos -> true);

        assertEquals(Optional.of(new BlockPos(0, SilverTreeLogic.MIN_TREE_LOGS + 1, 0)), target);
    }

    @Test
    void growthIgnoresLeafTipsWithoutLeavesOrAirAroundThem() {
        Set<BlockPos> logs = trunk(SilverTreeLogic.MIN_TREE_LOGS + 1);

        Optional<BlockPos> target = SilverTreeGrowth.findGrowthTarget(
                new BlockPos(0, 0, 0),
                logs::contains,
                pos -> pos.getY() == SilverTreeLogic.MIN_TREE_LOGS,
                pos -> false);

        assertTrue(target.isEmpty());
    }

    @Test
    void growthSkipsGroundLevelRootTips() {
        Set<BlockPos> logs = trunk(10);
        BlockPos rootTip = new BlockPos(3, 0, 0);
        logs.add(new BlockPos(1, 0, 0));
        logs.add(new BlockPos(2, 0, 0));
        logs.add(rootTip);
        BlockPos crownTip = new BlockPos(0, 9, 0);

        Optional<BlockPos> target = SilverTreeGrowth.findGrowthTarget(
                new BlockPos(0, 0, 0), logs::contains,
                pos -> pos.equals(rootTip) || pos.equals(crownTip), pos -> true);

        assertEquals(Optional.of(crownTip), target);
    }

    @Test
    void growthNeverStepsDownward() {
        assertFalse(SilverTreeGrowth.isLegalGrowthFace(Direction.DOWN, true, 1));
    }

    @Test
    void growthFaceNeedsAReplaceableTargetTouchingOnlyTheParent() {
        assertTrue(SilverTreeGrowth.isLegalGrowthFace(Direction.UP, true, 1));
        assertFalse(SilverTreeGrowth.isLegalGrowthFace(Direction.UP, false, 1));
        assertFalse(SilverTreeGrowth.isLegalGrowthFace(Direction.UP, true, 0));
        assertFalse(SilverTreeGrowth.isLegalGrowthFace(Direction.UP, true, 2));
    }

    @Test
    void leafPocketSkipsCornersAndItsCenter() {
        assertFalse(SilverTreeGrowth.isLeafPocketPosition(0, 0, 0));
        assertFalse(SilverTreeGrowth.isLeafPocketPosition(1, 1, 1));
        assertFalse(SilverTreeGrowth.isLeafPocketPosition(-1, 1, -1));

        assertTrue(SilverTreeGrowth.isLeafPocketPosition(1, 0, 0));
        assertTrue(SilverTreeGrowth.isLeafPocketPosition(0, -1, 0));
        assertTrue(SilverTreeGrowth.isLeafPocketPosition(1, 1, 0));
        assertTrue(SilverTreeGrowth.isLeafPocketPosition(0, 1, -1));
    }
}
