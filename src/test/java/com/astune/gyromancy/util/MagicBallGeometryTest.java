package com.astune.gyromancy.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicBallGeometryTest {

    @Test
    void voxelContainingTheCentreIsTheOnlyTouchedBlock() {
        List<BlockPos> positions =
                MagicBallGeometry.positionsInSphere(new Vec3(0.5, 0.5, 0.5), 0.2);

        assertEquals(List.of(new BlockPos(0, 0, 0)), positions);
    }

    @Test
    void smallSphereAtACornerTouchesEveryAdjacentVoxel() {
        List<BlockPos> positions =
                MagicBallGeometry.positionsInSphere(new Vec3(0.0, 0.0, 0.0), 0.4);

        assertEquals(8, positions.size());
        for (int x = -1; x <= 0; x++) {
            for (int y = -1; y <= 0; y++) {
                for (int z = -1; z <= 0; z++) {
                    assertTrue(positions.contains(new BlockPos(x, y, z)),
                            "expected " + x + "," + y + "," + z);
                }
            }
        }
    }

    @Test
    void touchingVoxelsCountEvenWhenTheirCentreIsOutside() {
        List<BlockPos> positions =
                MagicBallGeometry.positionsInSphere(new Vec3(0.0, 0.0, 0.0), 1.0);

        assertTrue(positions.contains(new BlockPos(1, 0, 0)));
        assertTrue(positions.contains(new BlockPos(1, -1, -1)));
        assertFalse(positions.contains(new BlockPos(2, 0, 0)));
        assertFalse(positions.contains(new BlockPos(1, 1, 0)));
    }

    @Test
    void nonPositiveOrInvalidRadiusYieldsNothing() {
        assertTrue(MagicBallGeometry.positionsInSphere(Vec3.ZERO, 0.0).isEmpty());
        assertTrue(MagicBallGeometry.positionsInSphere(Vec3.ZERO, -1.0).isEmpty());
        assertTrue(MagicBallGeometry.positionsInSphere(Vec3.ZERO, Double.NaN).isEmpty());
    }
}
