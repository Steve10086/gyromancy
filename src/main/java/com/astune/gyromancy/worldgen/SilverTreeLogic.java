package com.astune.gyromancy.worldgen;

import com.astune.gyromancy.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Shared queries for the silver tree shapes.
 *
 * <p>A silver log is a "leaf" log while exactly one of its six faces touches
 * another silver log. The stored {@code is_leaf} property mirrors that value,
 * but every gameplay decision re-derives it from the world: world generation
 * writes into proto chunks, where neither {@code onPlace} nor
 * {@code neighborChanged} fires, so the stored property cannot be trusted
 * before the chunk receives random ticks.</p>
 */
public final class SilverTreeLogic {

    /** Leaves are supported by any non-leaf silver log within this sphere. */
    public static final int LEAF_SUPPORT_RADIUS = 4;

    /** Safety cap for the connected-log walk performed by bone meal growth. */
    public static final int MAX_CONNECTED_LOGS = 4096;

    /** Bone meal only acts on trees made of at least this many logs. */
    public static final int MIN_TREE_LOGS = 9;

    private SilverTreeLogic() {}

    public static boolean isSilverLog(BlockState state) {
        return state.is(ModBlocks.SILVER_LOG.get());
    }

    public static int countAdjacentLogs(BlockGetter level, BlockPos pos) {
        int count = 0;
        for (Direction direction : Direction.values()) {
            if (isSilverLog(level.getBlockState(pos.relative(direction)))) {
                count++;
            }
        }
        return count;
    }

    public static boolean isLeafLog(BlockGetter level, BlockPos pos) {
        return isSilverLog(level.getBlockState(pos)) && countAdjacentLogs(level, pos) == 1;
    }

    /** True when a non-leaf silver log stands within the support radius. */
    public static boolean hasSupportingLog(BlockGetter level, BlockPos center) {
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-LEAF_SUPPORT_RADIUS, -LEAF_SUPPORT_RADIUS, -LEAF_SUPPORT_RADIUS),
                center.offset(LEAF_SUPPORT_RADIUS, LEAF_SUPPORT_RADIUS, LEAF_SUPPORT_RADIUS))) {
            if (pos.distSqr(center) > (double) LEAF_SUPPORT_RADIUS * LEAF_SUPPORT_RADIUS) {
                continue;
            }
            if (isSilverLog(level.getBlockState(pos)) && !isLeafLog(level, pos)) {
                return true;
            }
        }
        return false;
    }

    /** True when a naturally grown (non player-placed) silver leaf stands nearby. */
    public static boolean hasNativeSilverLeaves(BlockGetter level, BlockPos center) {
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-LEAF_SUPPORT_RADIUS, -LEAF_SUPPORT_RADIUS, -LEAF_SUPPORT_RADIUS),
                center.offset(LEAF_SUPPORT_RADIUS, LEAF_SUPPORT_RADIUS, LEAF_SUPPORT_RADIUS))) {
            if (pos.distSqr(center) > (double) LEAF_SUPPORT_RADIUS * LEAF_SUPPORT_RADIUS) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.SILVER_LEAVES.get()) && !state.getValue(LeavesBlock.PERSISTENT)) {
                return true;
            }
        }
        return false;
    }

    /** True when any of the six faces touches leaves or air. */
    public static boolean hasLeavesOrAirAdjacent(BlockGetter level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockState neighbor = level.getBlockState(pos.relative(direction));
            if (neighbor.isAir() || neighbor.is(BlockTags.LEAVES)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Breadth-first walk over positions accepted by {@code isLog}, starting at
     * {@code start}. Positions are returned in visit order and the walk stops
     * once {@code limit} positions were collected.
     */
    public static List<BlockPos> collectConnectedLogs(BlockPos start, Predicate<BlockPos> isLog, int limit) {
        List<BlockPos> order = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        BlockPos origin = start.immutable();
        if (!isLog.test(origin)) {
            return order;
        }
        visited.add(origin);
        queue.add(origin);
        while (!queue.isEmpty() && order.size() < limit) {
            BlockPos current = queue.poll();
            order.add(current);
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction).immutable();
                if (visited.add(next) && isLog.test(next)) {
                    queue.add(next);
                }
            }
        }
        return order;
    }

    public static Optional<BlockPos> firstQualifying(List<BlockPos> positions, Predicate<BlockPos> qualifies) {
        for (BlockPos pos : positions) {
            if (qualifies.test(pos)) {
                return Optional.of(pos);
            }
        }
        return Optional.empty();
    }
}
