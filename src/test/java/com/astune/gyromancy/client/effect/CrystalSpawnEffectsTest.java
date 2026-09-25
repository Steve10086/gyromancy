package com.astune.gyromancy.client.effect;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrystalSpawnEffectsTest {
    private static final double EPSILON = 1.0E-6;

    @Test
    void growthProgressIsClampedAndSmoothstepped() {
        assertEquals(0.0F, CrystalSpawnEffects.growthProgress(-5.0F), EPSILON);
        assertEquals(0.0F, CrystalSpawnEffects.growthProgress(0.0F), EPSILON);
        assertEquals(0.5F, CrystalSpawnEffects.growthProgress(10.0F), EPSILON);
        assertEquals(1.0F, CrystalSpawnEffects.growthProgress(20.0F), EPSILON);
        assertEquals(1.0F, CrystalSpawnEffects.growthProgress(50.0F), EPSILON);
    }

    @Test
    void trackedCrystalGrowsFromTenPercentToFullSizeInTwentyTicks() {
        BlockPos pos = new BlockPos(12, 64, -7);
        CrystalSpawnEffects.clear();
        CrystalSpawnEffects.startGrowth(pos, 100L);

        assertEquals(CrystalSpawnEffects.START_SCALE,
                CrystalSpawnEffects.scale(pos, 100L, 0.0F), EPSILON);
        assertEquals(0.55F, CrystalSpawnEffects.scale(pos, 110L, 0.0F), EPSILON);
        assertEquals(1.0F, CrystalSpawnEffects.scale(pos, 120L, 0.0F), EPSILON);
        // Finished crystals stop being tracked and stay at full size.
        assertEquals(1.0F, CrystalSpawnEffects.scale(pos, 121L, 0.0F), EPSILON);
    }

    @Test
    void untrackedCrystalRendersAtFullSize() {
        CrystalSpawnEffects.clear();

        assertEquals(1.0F,
                CrystalSpawnEffects.scale(new BlockPos(0, 70, 0), 500L, 0.5F), EPSILON);
    }
}
