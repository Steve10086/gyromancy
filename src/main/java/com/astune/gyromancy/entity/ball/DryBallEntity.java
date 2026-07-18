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
        super(type, level);
    }

    public DryBallEntity(Level level, Vec3 pos, Vec3 velocity, float size) {
        this(ModEntities.DRY_BALL.get(), level);
        configure(pos, velocity, size);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || tickCount % EFFECT_INTERVAL != 0) return;
        List<BlockPos> positions = containedPositions(getTargetSize());
        for (BlockPos pos : positions) {
            if (!level().getBlockState(pos).is(Blocks.WATER)) continue;
            long availableMana = positions.stream()
                    .mapToLong(p -> Math.max(0L, ElementStorageManager.INSTANCE.get(level(), p).get(ElementType.MANA)))
                    .sum();
            if (availableMana >= WATER_BLOCK_MANA_COST) {
                consumeElement(positions, ElementType.MANA, WATER_BLOCK_MANA_COST);
                level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        reduceWaterElement(positions);
    }

    private void reduceWaterElement(List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            var current = ElementStorageManager.INSTANCE.get(level(), pos);
            long mana = Math.max(0L, current.get(ElementType.MANA));
            long water = Math.max(0L, current.get(ElementType.WATER));
            long removedWater = Math.min(water, mana / 2L);
            ElementStorageManager.INSTANCE.set(level(), pos, current
                    .withValue(ElementType.WATER, water - removedWater)
                    .withValue(ElementType.MANA, mana - removedWater * 2L));
        }
    }
}
