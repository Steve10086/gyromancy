package com.astune.gyromancy.entity.ball;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class IceBallEntity extends ResistantBallEntity {
    private static final int EFFECT_INTERVAL = 10;
    private static final long FIRE_MANA_COST = 2L;
    private static final long TIME_MANA_COST = 100L;

    public IceBallEntity(EntityType<IceBallEntity> type, Level level) {
        super(type, level, ElementType.WATER);
    }

    public IceBallEntity(Level level, Vec3 pos, Vec3 velocity, double arrowSizeSum, double liftDirection, float size) {
        this(ModEntities.ICE_BALL.get(), level);
        configure(pos, velocity, arrowSizeSum, liftDirection, size);
    }

    @Override
    protected void tickAfterPayload() {
        if (!(level() instanceof ServerLevel server)) return;
        if (tickCount % EFFECT_INTERVAL == 0) {
            reduceElementWithMana(ElementType.FIRE, FIRE_MANA_COST);
            reduceElementWithMana(ElementType.TIME, TIME_MANA_COST);
        }
        applyColdBlocks(server);
    }

    private void applyColdBlocks(ServerLevel level) {
        int randomTickSpeed = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        if (randomTickSpeed <= 0) return;
        for (BlockPos pos : containedPositions(getTargetSize())) {
            if (level.random.nextInt(4096) >= randomTickSpeed) continue;
            freezeWater(level, pos);
            placeSnow(level, pos.above());
        }
    }

    private void freezeWater(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(Blocks.WATER) || !level.getFluidState(pos).isSource()) return;
        BlockState ice = Blocks.FROSTED_ICE.defaultBlockState();
        if (level.setBlockAndUpdate(pos, ice)) {
            level.scheduleTick(pos, Blocks.FROSTED_ICE, level.random.nextInt(60, 121));
        }
    }

    private void placeSnow(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockState snow = state.is(Blocks.SNOW)
                ? state.setValue(SnowLayerBlock.LAYERS, Math.min(8, state.getValue(SnowLayerBlock.LAYERS) + 1))
                : Blocks.SNOW.defaultBlockState();
        if ((state.isAir() || state.is(Blocks.SNOW)) && snow.canSurvive(level, pos)) level.setBlockAndUpdate(pos, snow);
    }

}
