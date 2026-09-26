package com.astune.gyromancy.worldgen;

import com.astune.gyromancy.block.SilverLogBlock;
import com.astune.gyromancy.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Bone meal driven growth for natural silver trees.
 *
 * <p>The clicked log's connected component is walked through the six adjacent
 * faces. Trees smaller than {@link SilverTreeLogic#MIN_TREE_LOGS} logs cannot
 * grow; otherwise the first elevated leaf tip that touches leaves or air becomes
 * the parent. Ground-level root tips are excluded. A new log is placed at a
 * random legal face of that parent and the surrounding 3x3x3 air pocket, minus
 * its eight corners, is filled with silver leaves.</p>
 *
 * <p>Growth never steps downward, and the target position must not touch any
 * silver log other than its parent. Most crafts grow from a leaf tip; one in
 * ten instead sprouts from a natural, non-tip log so the tree can branch from
 * its trunk or from the middle of an arm.</p>
 */
public final class SilverTreeGrowth {

    /** Chance to grow from a non-tip log instead of a leaf tip. */
    static final int MIDDLE_GROWTH_CHANCE = 10;

    private SilverTreeGrowth() {}

    /** Returns the position of the new log when the tree grew. */
    public static Optional<BlockPos> tryGrow(ServerLevel level, BlockPos clickedPos, RandomSource random) {
        boolean fromMiddle = random.nextInt(MIDDLE_GROWTH_CHANCE) == 0;
        Optional<BlockPos> target = findGrowthTarget(
                clickedPos,
                pos -> SilverTreeLogic.isSilverLog(level.getBlockState(pos)),
                pos -> fromMiddle
                        ? SilverTreeLogic.isOriginalLog(level, pos)
                        && !SilverTreeLogic.isLeafLog(level, pos)
                        : SilverTreeLogic.isLeafLog(level, pos),
                pos -> SilverTreeLogic.hasLeavesOrAirAdjacent(level, pos));
        if (target.isEmpty()) {
            return Optional.empty();
        }
        BlockPos parent = target.get();
        List<Direction> legalFaces = new ArrayList<>(Direction.values().length);
        for (Direction direction : Direction.values()) {
            BlockPos candidate = parent.relative(direction);
            if (isLegalGrowthFace(direction,
                    canBeReplacedByLog(level.getBlockState(candidate)),
                    SilverTreeLogic.countAdjacentLogs(level, candidate))) {
                legalFaces.add(direction);
            }
        }
        if (legalFaces.isEmpty()) {
            return Optional.empty();
        }
        Direction direction = legalFaces.get(random.nextInt(legalFaces.size()));
        BlockPos newLogPos = parent.relative(direction);
        BlockState newLog = ModBlocks.SILVER_LOG.get().defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, direction.getAxis())
                .setValue(SilverLogBlock.IS_LEAF, true)
                .setValue(SilverLogBlock.ORIGINAL, true);
        level.setBlock(newLogPos, newLog, Block.UPDATE_ALL);
        placeLeafPocket(level, newLogPos);
        return Optional.of(newLogPos);
    }

    /**
     * A face may grow when it does not point down, the target is replaceable and
     * the only silver log touching the target is the parent.
     */
    static boolean isLegalGrowthFace(Direction direction, boolean replaceable, int adjacentLogs) {
        return direction != Direction.DOWN && replaceable && adjacentLogs == 1;
    }

    static Optional<BlockPos> findGrowthTarget(BlockPos origin,
                                               Predicate<BlockPos> isLog,
                                               Predicate<BlockPos> isLeaf,
                                               Predicate<BlockPos> hasLeavesOrAir) {
        List<BlockPos> logs = SilverTreeLogic.collectConnectedLogs(
                origin, isLog, SilverTreeLogic.MAX_CONNECTED_LOGS);
        if (logs.size() < SilverTreeLogic.MIN_TREE_LOGS) {
            return Optional.empty();
        }
        int lowestY = logs.stream().mapToInt(BlockPos::getY).min().orElse(origin.getY());
        return SilverTreeLogic.firstQualifying(logs, pos -> pos.getY() >= lowestY + 2
                && isLeaf.test(pos) && hasLeavesOrAir.test(pos));
    }

    private static boolean canBeReplacedByLog(BlockState state) {
        return state.isAir() || state.is(BlockTags.LEAVES) || state.canBeReplaced();
    }

    private static void placeLeafPocket(ServerLevel level, BlockPos center) {
        BlockState leaves = ModBlocks.SILVER_LEAVES.get().defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
            int dx = pos.getX() - center.getX();
            int dy = pos.getY() - center.getY();
            int dz = pos.getZ() - center.getZ();
            if (isLeafPocketPosition(dx, dy, dz) && level.getBlockState(pos).isAir()) {
                level.setBlock(pos, leaves, Block.UPDATE_ALL);
            }
        }
    }

    /** The 3x3x3 pocket minus its center and eight corners. */
    static boolean isLeafPocketPosition(int dx, int dy, int dz) {
        int ax = Math.abs(dx);
        int ay = Math.abs(dy);
        int az = Math.abs(dz);
        if (ax == 0 && ay == 0 && az == 0) {
            return false;
        }
        return !(ax == 1 && ay == 1 && az == 1);
    }
}
