package com.astune.gyromancy.symbol;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FloodFillExtractorTest {

    @Test
    void adjacentBlockForHorizontalFacesDoesNotChangeHeight() {
        BlockPos base = new BlockPos(10, 20, 30);

        assertEquals(
                new BlockPos(9, 20, 31),
                FloodFillExtractor.adjacentBlockForEdge(
                        base, Direction.UP, new Vec3(9.9, 21.0, 31.1)));
    }

    @Test
    void adjacentBlockForVerticalFacesKeepsFaceNormalAxis() {
        BlockPos base = new BlockPos(10, 20, 30);

        assertEquals(
                new BlockPos(9, 21, 30),
                FloodFillExtractor.adjacentBlockForEdge(
                        base, Direction.NORTH, new Vec3(9.9, 21.1, 29.0)));
        assertEquals(
                new BlockPos(10, 21, 31),
                FloodFillExtractor.adjacentBlockForEdge(
                        base, Direction.EAST, new Vec3(11.0, 21.1, 31.1)));
    }
}
