package com.astune.gyromancy.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Plans a silver tree as connected paths before any blocks are placed. */
final class SilverTreeShape {

    static final int MIN_HEIGHT = 7;
    static final int MAX_HEIGHT = 15;

    private static final double FULL_TURN = Math.PI * 2.0;
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private SilverTreeShape() {}

    static Plan create(BlockPos origin, RandomSource random) {
        int height = MIN_HEIGHT + random.nextInt(MAX_HEIGHT - MIN_HEIGHT + 1);
        int trunkHeight = (height + 1) / 2;
        List<BlockPos> footprint = trunkFootprint(origin, random.nextBoolean());
        Map<BlockPos, Direction.Axis> logs = new HashMap<>();
        Set<BlockPos> leaves = new HashSet<>();
        List<List<BlockPos>> rootPaths = new ArrayList<>();
        List<Integer> forkHeights = new ArrayList<>();

        // The thick trunk can lean one block in its upper half while keeping its section.
        boolean leans = random.nextInt(3) != 0;
        Direction leanDirection = HORIZONTAL[random.nextInt(HORIZONTAL.length)];
        int leanAt = 2 + random.nextInt(Math.max(1, trunkHeight - 2));
        for (int y = 0; y < trunkHeight; y++) {
            for (BlockPos base : layerFootprint(footprint, leans && y >= leanAt, leanDirection)) {
                logs.put(base.above(y), Direction.Axis.Y);
            }
        }

        // Roots leave the foot at different angles and each makes a small bend.
        int rootCount = 3 + random.nextInt(2);
        double rootPhase = random.nextDouble() * FULL_TURN;
        for (int root = 0; root < rootCount; root++) {
            double angle = rootPhase + FULL_TURN * root / rootCount + jitter(random, 0.24);
            double curve = (random.nextBoolean() ? 1 : -1) * (0.25 + random.nextDouble() * 0.4);
            int length = 3 + random.nextInt(4);
            int bendAt = 2 + random.nextInt(length - 1);
            Direction startingDirection = dominantDirection(angle);
            boolean raisedRoot = random.nextBoolean();
            BlockPos current = edge(footprint, angle, random).above(raisedRoot ? 1 : 0);
            List<BlockPos> path = new ArrayList<>();
            for (int step = 1; step <= length; step++) {
                double heading = angle + curve * step / length + jitter(random, 0.12);
                Direction movement = step == 1 ? startingDirection
                        : step == bendAt
                        ? outwardPerpendicular(startingDirection, angle, curve > 0)
                        : stepDirection(heading, random);
                if (headingDot(movement, angle) < -0.01) {
                    movement = startingDirection;
                }
                current = current.relative(movement);
                logs.put(current, movement.getAxis());
                path.add(current);
                if (raisedRoot && step == 2) {
                    current = current.below();
                    logs.put(current, Direction.Axis.Y);
                    path.add(current);
                }
            }
            rootPaths.add(List.copyOf(path));
        }

        // Primary branches emerge at different levels of the upper trunk.
        int branchCount = 2 + random.nextInt(2);
        double branchPhase = random.nextDouble() * FULL_TURN;
        int lowestFork = Math.max(2, trunkHeight - 3);
        for (int branch = 0; branch < branchCount; branch++) {
            int forkY = lowestFork + random.nextInt(trunkHeight - lowestFork);
            if (branch == branchCount - 1 && forkHeights.stream().allMatch(y -> y == trunkHeight - 1)) {
                forkY = trunkHeight - 2;
            }
            forkHeights.add(forkY);

            double angle = branchPhase + FULL_TURN * branch / branchCount + jitter(random, 0.34);
            double curve = (random.nextBoolean() ? 1 : -1) * (0.2 + random.nextDouble() * 0.55);
            int targetY = branch == 0 ? height - 2
                    : Math.max(forkY + 1, height - 2 - random.nextInt(3));
            int rise = targetY - forkY;
            int length = Math.max(rise + 2, 3 + height / 2 + random.nextInt(3));
            int climbEnd = Math.max(2, length / 2);
            int bendAt = 2 + random.nextInt(length - 3);
            Direction startingDirection = dominantDirection(angle);
            List<BlockPos> forkFootprint = layerFootprint(
                    footprint, leans && forkY >= leanAt, leanDirection);
            BlockPos current = edge(forkFootprint, angle, random).above(forkY);
            List<BranchPoint> path = new ArrayList<>();
            for (int step = 1; step <= length; step++) {
                double heading = angle + curve * step / length + jitter(random, 0.24);
                Direction movement = step == 1 ? startingDirection
                        : step == bendAt
                        ? outwardPerpendicular(startingDirection, angle, curve > 0)
                        : stepDirection(heading, random);
                if (headingDot(movement, angle) < -0.01) {
                    movement = startingDirection;
                }
                current = current.relative(movement);
                logs.put(current, movement.getAxis());

                int targetAtStep = forkY + (step == 1 ? 0
                        : Math.min(rise, (step - 1) * rise / (climbEnd - 1)));
                while (current.getY() < origin.getY() + targetAtStep) {
                    current = current.above();
                    logs.put(current, Direction.Axis.Y);
                }
                path.add(new BranchPoint(current, movement));
            }

            int leafOffset = random.nextInt(2);
            for (int i = 0; i < path.size(); i++) {
                if (i == 0 || i == path.size() - 1 || (i + leafOffset) % 2 == 0) {
                    BranchPoint point = path.get(i);
                    addLeafSheet(leaves, point.pos(), point.direction(),
                            i == path.size() - 1, random);
                }
            }

            // Short secondary forks break the outline of otherwise continuous arms.
            if (path.size() >= 6 && random.nextInt(3) != 0) {
                int attach = 2 + random.nextInt(path.size() - 4);
                BranchPoint parent = path.get(attach);
                Direction side = perpendicular(parent.direction(), random.nextBoolean());
                BlockPos twig = parent.pos();
                int twigLength = 2 + random.nextInt(3);
                for (int step = 1; step <= twigLength; step++) {
                    Direction movement = step == twigLength && random.nextBoolean()
                            ? parent.direction() : side;
                    twig = twig.relative(movement);
                    logs.put(twig, movement.getAxis());
                    if (step == twigLength || step == 2) {
                        addLeafSheet(leaves, twig, side, step == twigLength, random);
                    }
                }
            }
        }

        // Asymmetric small sheets also cross the upper trunk below the forks.
        int trunkSheets = 2 + random.nextInt(3);
        for (int i = 0; i < trunkSheets; i++) {
            int y = Math.max(1, trunkHeight - 3) + random.nextInt(Math.min(3, trunkHeight - 1));
            double angle = random.nextDouble() * FULL_TURN;
            List<BlockPos> atLayer = layerFootprint(
                    footprint, leans && y >= leanAt, leanDirection);
            addLeafSheet(leaves, edge(atLayer, angle, random).above(y),
                    dominantDirection(angle), false, random);
        }

        leaves.removeAll(logs.keySet());
        Set<BlockPos> rootLogs = new HashSet<>();
        for (List<BlockPos> path : rootPaths) {
            rootLogs.addAll(path);
        }
        Set<BlockPos> supportingLogs = new HashSet<>();
        for (BlockPos log : logs.keySet()) {
            if (rootLogs.contains(log)) {
                continue;
            }
            int neighbors = 0;
            for (Direction direction : Direction.values()) {
                if (logs.containsKey(log.relative(direction))) {
                    neighbors++;
                }
            }
            if (neighbors > 1) {
                supportingLogs.add(log);
            }
        }
        int supportRadiusSquared = SilverTreeLogic.LEAF_SUPPORT_RADIUS
                * SilverTreeLogic.LEAF_SUPPORT_RADIUS;
        leaves.removeIf(leaf -> supportingLogs.stream()
                .noneMatch(log -> log.distSqr(leaf) <= supportRadiusSquared));
        return new Plan(height, trunkHeight, branchCount, List.copyOf(footprint),
                List.copyOf(rootPaths), List.copyOf(forkHeights),
                Map.copyOf(logs), Set.copyOf(leaves));
    }

    private static List<BlockPos> trunkFootprint(BlockPos origin, boolean crossSection) {
        List<BlockPos> footprint = new ArrayList<>(crossSection ? 5 : 4);
        footprint.add(origin);
        if (crossSection) {
            for (Direction direction : HORIZONTAL) {
                footprint.add(origin.relative(direction));
            }
        } else {
            footprint.add(origin.east());
            footprint.add(origin.south());
            footprint.add(origin.east().south());
        }
        return footprint;
    }

    private static List<BlockPos> layerFootprint(List<BlockPos> base, boolean leans,
                                                  Direction leanDirection) {
        if (!leans) {
            return base;
        }
        List<BlockPos> moved = new ArrayList<>(base.size());
        for (BlockPos pos : base) {
            moved.add(pos.relative(leanDirection));
        }
        return moved;
    }

    private static BlockPos edge(List<BlockPos> footprint, double angle, RandomSource random) {
        double x = Math.cos(angle);
        double z = Math.sin(angle);
        double outermost = Double.NEGATIVE_INFINITY;
        List<BlockPos> choices = new ArrayList<>(2);
        for (BlockPos base : footprint) {
            double projection = base.getX() * x + base.getZ() * z;
            if (projection > outermost + 0.0001) {
                outermost = projection;
                choices.clear();
            }
            if (projection >= outermost - 0.0001) {
                choices.add(base);
            }
        }
        return choices.get(random.nextInt(choices.size()));
    }

    private static Direction dominantDirection(double angle) {
        double x = Math.cos(angle);
        double z = Math.sin(angle);
        return Math.abs(x) >= Math.abs(z)
                ? (x >= 0 ? Direction.EAST : Direction.WEST)
                : (z >= 0 ? Direction.SOUTH : Direction.NORTH);
    }

    private static Direction stepDirection(double angle, RandomSource random) {
        double x = Math.cos(angle);
        double z = Math.sin(angle);
        return random.nextDouble() * (Math.abs(x) + Math.abs(z)) < Math.abs(x)
                ? (x >= 0 ? Direction.EAST : Direction.WEST)
                : (z >= 0 ? Direction.SOUTH : Direction.NORTH);
    }

    private static Direction perpendicular(Direction direction, boolean clockwise) {
        return clockwise ? direction.getClockWise() : direction.getCounterClockWise();
    }

    private static Direction outwardPerpendicular(Direction direction, double angle,
                                                   boolean clockwise) {
        Direction preferred = perpendicular(direction, clockwise);
        return headingDot(preferred, angle) >= -0.01
                ? preferred : perpendicular(direction, !clockwise);
    }

    private static double headingDot(Direction direction, double angle) {
        return direction.getStepX() * Math.cos(angle) + direction.getStepZ() * Math.sin(angle);
    }

    private static double jitter(RandomSource random, double radius) {
        return (random.nextDouble() * 2 - 1) * radius;
    }

    private static void addLeafSheet(Set<BlockPos> leaves, BlockPos log, Direction direction,
                                     boolean tip, RandomSource random) {
        Direction side = direction.getClockWise();
        int halfWidth = tip ? 3 : 2 + random.nextInt(2);
        BlockPos center = halfWidth == 3 ? log : log.relative(side, random.nextInt(3) - 1);
        int reach = 2;
        for (int forward = -1; forward <= reach; forward++) {
            for (int lateral = -halfWidth; lateral <= halfWidth; lateral++) {
                if (Math.abs(forward) == reach && Math.abs(lateral) == halfWidth) {
                    continue;
                }
                if (Math.abs(lateral) == halfWidth && random.nextInt(4) == 0) {
                    continue;
                }
                leaves.add(center.relative(direction, forward).relative(side, lateral));
            }
        }
        if (tip) {
            BlockPos upper = log.above();
            leaves.add(upper);
            leaves.add(upper.relative(direction));
            leaves.add(upper.relative(direction.getOpposite()));
            leaves.add(upper.relative(side));
            leaves.add(upper.relative(side.getOpposite()));
        }
    }

    private record BranchPoint(BlockPos pos, Direction direction) {}

    record Plan(int height, int trunkHeight, int branchCount, List<BlockPos> footprint,
                List<List<BlockPos>> rootPaths, List<Integer> forkHeights,
                Map<BlockPos, Direction.Axis> logs, Set<BlockPos> leaves) {}
}
