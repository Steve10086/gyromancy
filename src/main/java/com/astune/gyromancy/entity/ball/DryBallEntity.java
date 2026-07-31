package com.astune.gyromancy.entity.ball;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.element.ElementStorageManager;
import com.astune.gyromancy.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class DryBallEntity extends ResistantBallEntity {
    private static final int EFFECT_INTERVAL = 10;
    private static final long WATER_BLOCK_MANA_COST = 1000L;

    public DryBallEntity(EntityType<DryBallEntity> type, Level level) {
        super(type, level, ElementType.WATER);
    }

    public DryBallEntity(Level level, Vec3 pos, Vec3 velocity, double arrowSizeSum, double liftDirection, float size) {
        this(ModEntities.DRY_BALL.get(), level);
        configure(pos, velocity, arrowSizeSum, liftDirection, size);
    }

    @Override
    protected void tickAfterPayload() {
        if (level().isClientSide || tickCount % EFFECT_INTERVAL != 0) return;
        List<BlockPos> positions = containedPositions(getTargetSize());
        long availableMana = ElementStorageManager.INSTANCE.sumPositive(
                level(), positions, ElementType.MANA);
        for (BlockPos pos : positions) {
            if (!level().getBlockState(pos).is(Blocks.WATER)) continue;
            if (availableMana >= WATER_BLOCK_MANA_COST) {
                long consumed = consumeElement(
                        positions, ElementType.MANA, WATER_BLOCK_MANA_COST);
                availableMana -= consumed;
                level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        reduceElementWithMana(ElementType.WATER, 2L);
    }
}
