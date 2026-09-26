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
 * grow; otherwise the first walked log that is a leaf tip and touches leaves or
 * air becomes the parent. A new log is placed at a random legal face of that
 * parent and the surrounding 3x3x3 air pocket is filled with silver leaves.</p>
 */
public final class SilverTreeGrowth {

    private SilverTreeGrowth() {}

    public static boolean tryGrow(ServerLevel level, BlockPos clickedPos, RandomSource random) {
        Optional<BlockPos> target = findGrowthTarget(
                clickedPos,
                pos -> SilverTreeLogic.isSilverLog(level.getBlockState(pos)),
                pos -> SilverTreeLogic.isLeafLog(level, pos),
                pos -> SilverTreeLogic.hasLeavesOrAirAdjacent(level, pos));
        if (target.isEmpty()) {
            return false;
        }
        BlockPos parent = target.get();
        List<Direction> legalFaces = new ArrayList<>(Direction.values().length);
        for (Direction direction : Direction.values()) {
            if (canBeReplacedByLog(level.getBlockState(parent.relative(direction)))) {
                legalFaces.add(direction);
            }
        }
        if (legalFaces.isEmpty()) {
            return false;
        }
        Direction direction = legalFaces.get(random.nextInt(legalFaces.size()));
        BlockPos newLogPos = parent.relative(direction);
        BlockState newLog = ModBlocks.SILVER_LOG.get().defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, direction.getAxis())
                .setValue(SilverLogBlock.IS_LEAF, SilverTreeLogic.countAdjacentLogs(level, newLogPos) == 1)
                .setValue(SilverLogBlock.ORIGINAL, true);
        level.setBlock(newLogPos, newLog, Block.UPDATE_ALL);
        placeLeafPocket(level, newLogPos);
        return true;
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
        return SilverTreeLogic.firstQualifying(logs, pos -> isLeaf.test(pos) && hasLeavesOrAir.test(pos));
    }

    private static boolean canBeReplacedByLog(BlockState state) {
        return state.isAir() || state.is(BlockTags.LEAVES) || state.canBeReplaced();
    }

    private static void placeLeafPocket(ServerLevel level, BlockPos center) {
        BlockState leaves = ModBlocks.SILVER_LEAVES.get().defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
            if (!pos.equals(center) && level.getBlockState(pos).isAir()) {
                level.setBlock(pos, leaves, Block.UPDATE_ALL);
            }
        }
    }
}
