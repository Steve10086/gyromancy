package com.astune.gyromancy.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SilverTreeShapeTest {

    @Test
    void plannedTreesMeetTheGeometryAndLeafSupportRequirements() {
        BlockPos origin = new BlockPos(0, 70, 0);
        Set<String> silhouettes = new HashSet<>();
        for (int seed = 0; seed < 128; seed++) {
            SilverTreeShape.Plan tree = SilverTreeShape.create(origin, RandomSource.create(seed));
            Set<BlockPos> logs = tree.logs().keySet();

            assertTrue(tree.height() >= 7 && tree.height() <= 15);
            assertEquals((tree.height() + 1) / 2, tree.trunkHeight());
            assertTrue(tree.footprint().size() == 4 || tree.footprint().size() == 5);
            assertTrue(tree.branchCount() == 2 || tree.branchCount() == 3);
            assertEquals(tree.branchCount(), tree.forkHeights().size());
            assertTrue(tree.forkHeights().stream().anyMatch(y -> y < tree.trunkHeight() - 1));
            for (int y = 0; y < tree.trunkHeight(); y++) {
                int layer = y;
                List<BlockPos> possibleLeans = List.of(origin, origin.north(), origin.east(),
                        origin.south(), origin.west());
                assertTrue(possibleLeans.stream().anyMatch(shift -> tree.footprint().stream()
                        .allMatch(base -> logs.contains(base.offset(shift.getX() - origin.getX(),
                                layer, shift.getZ() - origin.getZ())))),
                        "incomplete wide trunk at seed " + seed);
            }

            assertTrue(tree.rootPaths().size() == 3 || tree.rootPaths().size() == 4);
            for (List<BlockPos> root : tree.rootPaths()) {
                assertTrue(root.size() >= 3 && root.size() <= 7);
                assertTrue(tree.footprint().stream().anyMatch(base ->
                        base.distManhattan(root.getFirst()) == 1
                        || base.above().distManhattan(root.getFirst()) == 1));
                Set<Direction.Axis> horizontalAxes = new HashSet<>();
                for (BlockPos base : tree.footprint()) {
                    BlockPos atRootHeight = base.atY(root.getFirst().getY());
                    if (atRootHeight.distManhattan(root.getFirst()) == 1) {
                        horizontalAxes.add(atRootHeight.getX() != root.getFirst().getX()
                                ? Direction.Axis.X : Direction.Axis.Z);
                    }
                }
                for (int i = 0; i < root.size(); i++) {
                    assertTrue(root.get(i).getY() == origin.getY()
                            || root.get(i).getY() == origin.getY() + 1);
                    if (i > 0) {
                        assertEquals(1, root.get(i - 1).distManhattan(root.get(i)));
                        if (root.get(i - 1).getX() != root.get(i).getX()) {
                            horizontalAxes.add(Direction.Axis.X);
                        }
                        if (root.get(i - 1).getZ() != root.get(i).getZ()) {
                            horizontalAxes.add(Direction.Axis.Z);
                        }
                    }
                }
                assertEquals(2, horizontalAxes.size(), "straight root at seed " + seed);
            }

            Set<BlockPos> connected = new HashSet<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            connected.add(origin);
            queue.add(origin);
            while (!queue.isEmpty()) {
                BlockPos current = queue.removeFirst();
                for (Direction direction : Direction.values()) {
                    BlockPos next = current.relative(direction);
                    if (logs.contains(next) && connected.add(next)) {
                        queue.addLast(next);
                    }
                }
            }
            assertEquals(logs.size(), connected.size(), "disconnected log at seed " + seed);

            int maxY = logs.stream().mapToInt(BlockPos::getY).max().orElseThrow();
            maxY = Math.max(maxY, tree.leaves().stream().mapToInt(BlockPos::getY).max().orElseThrow());
            assertEquals(origin.getY() + tree.height() - 1, maxY, "wrong height at seed " + seed);
            assertTrue(tree.leaves().stream().noneMatch(logs::contains));
            for (BlockPos leaf : tree.leaves()) {
                assertTrue(logs.stream().anyMatch(log -> log.distSqr(leaf) <= 16
                        && adjacentLogCount(log, logs) > 1),
                        "unsupported leaf at seed " + seed + " position " + leaf);
            }
            silhouettes.add(logs.stream().map(pos -> (pos.getX() - origin.getX()) + ","
                    + (pos.getY() - origin.getY()) + "," + (pos.getZ() - origin.getZ()))
                    .sorted().collect(Collectors.joining(";")));
        }
        assertTrue(silhouettes.size() > 100, "different seeds produced too few tree shapes");
    }

    private static int adjacentLogCount(BlockPos pos, Set<BlockPos> logs) {
        int count = 0;
        for (Direction direction : Direction.values()) {
            if (logs.contains(pos.relative(direction))) {
                count++;
            }
        }
        return count;
    }

    @Test
    void treesOfTheSameHeightHaveDifferentBranchShapes() {
        BlockPos origin = BlockPos.ZERO;
        Map<Integer, Set<String>> branchShapesByHeight = new HashMap<>();
        for (int seed = 0; seed < 512; seed++) {
            SilverTreeShape.Plan tree = SilverTreeShape.create(origin, RandomSource.create(seed));
            String branches = tree.logs().keySet().stream()
                    .filter(pos -> pos.getY() >= tree.trunkHeight())
                    .map(pos -> pos.getX() + "," + pos.getY() + "," + pos.getZ())
                    .sorted().collect(Collectors.joining(";"));
            branchShapesByHeight.computeIfAbsent(tree.height(), ignored -> new HashSet<>())
                    .add(branches);
        }
        for (int height = SilverTreeShape.MIN_HEIGHT; height <= SilverTreeShape.MAX_HEIGHT; height++) {
            assertTrue(branchShapesByHeight.getOrDefault(height, Set.of()).size() >= 20,
                    "branches repeat too often at height " + height);
        }
    }
}
