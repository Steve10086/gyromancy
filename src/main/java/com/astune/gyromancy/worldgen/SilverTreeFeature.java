package com.astune.gyromancy.worldgen;

import com.astune.gyromancy.block.SilverLogBlock;
import com.astune.gyromancy.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Places the wide trunk, surface roots, spreading branches, and sheet foliage of a silver tree. */
public final class SilverTreeFeature extends Feature<NoneFeatureConfiguration> {

    public SilverTreeFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        SilverTreeShape.Plan shape = SilverTreeShape.create(context.origin(), context.random());
        Set<BlockPos> logPositions = new HashSet<>(shape.logs().keySet());
        for (List<BlockPos> rootPath : shape.rootPaths()) {
            logPositions.removeAll(rootPath);
        }
        BlockState sapling = ModBlocks.SILVER_SAPLING.get().defaultBlockState();

        for (BlockPos base : shape.footprint()) {
            if (!sapling.canSurvive(level, base)) {
                return false;
            }
        }
        for (BlockPos pos : logPositions) {
            if (outsideBuildHeight(level, pos) || !canReplace(level.getBlockState(pos))) {
                return false;
            }
        }
        int rootArms = 0;
        for (List<BlockPos> rootPath : shape.rootPaths()) {
            List<BlockPos> clearPrefix = new ArrayList<>(rootPath.size());
            for (BlockPos pos : rootPath) {
                if (outsideBuildHeight(level, pos) || !canReplace(level.getBlockState(pos))) {
                    break;
                }
                clearPrefix.add(pos);
            }
            if (clearPrefix.size() >= 2) {
                logPositions.addAll(clearPrefix);
                rootArms++;
            }
        }
        if (rootArms < 2) {
            return false;
        }

        // A top leaf makes the planned height the actual above-ground height.
        boolean hasTopLeaf = false;
        int topY = context.origin().getY() + shape.height() - 1;
        for (BlockPos pos : shape.leaves()) {
            if (pos.getY() == topY && !outsideBuildHeight(level, pos)
                    && canReplace(level.getBlockState(pos))) {
                hasTopLeaf = true;
                break;
            }
        }
        if (!hasTopLeaf) {
            return false;
        }

        BlockState log = ModBlocks.SILVER_LOG.get().defaultBlockState()
                .setValue(SilverLogBlock.ORIGINAL, true);
        for (Map.Entry<BlockPos, Direction.Axis> entry : shape.logs().entrySet()) {
            BlockPos pos = entry.getKey();
            if (!logPositions.contains(pos)) {
                continue;
            }
            boolean tip = adjacentLogs(pos, logPositions) == 1;
            level.setBlock(pos, log.setValue(RotatedPillarBlock.AXIS, entry.getValue())
                    .setValue(SilverLogBlock.IS_LEAF, tip), Block.UPDATE_ALL);
        }

        BlockState leaves = ModBlocks.SILVER_LEAVES.get().defaultBlockState()
                .setValue(LeavesBlock.PERSISTENT, false);
        for (BlockPos pos : shape.leaves()) {
            if (!outsideBuildHeight(level, pos) && canReplace(level.getBlockState(pos))) {
                level.setBlock(pos, leaves, Block.UPDATE_ALL);
            }
        }
        return true;
    }

    private static int adjacentLogs(BlockPos pos, Set<BlockPos> logs) {
        int count = 0;
        for (Direction direction : Direction.values()) {
            if (logs.contains(pos.relative(direction))) {
                count++;
            }
        }
        return count;
    }

    private static boolean outsideBuildHeight(WorldGenLevel level, BlockPos pos) {
        return pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight();
    }

    private static boolean canReplace(BlockState state) {
        return state.getFluidState().isEmpty()
                && (state.isAir() || state.is(BlockTags.LEAVES)
                || state.is(ModBlocks.SILVER_SAPLING.get()) || state.canBeReplaced());
    }
}
