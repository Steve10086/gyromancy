package com.astune.gyromancy.compile.operator;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class CrystalOpCodecTest {
    private static final double EPSILON = 1.0E-9;

    @Test
    void roundTripsDefaultCrystalOpsThroughOwnCodecs() {
        List<CrystalGenOp> ops = List.of(
                new FireCrystalOp(), new WaterCrystalOp(), new WindCrystalOp(), new EarthCrystalOp());

        for (CrystalGenOp op : ops) {
            CrystalGenOp loaded = assertInstanceOf(op.getClass(),
                    EntityPayload.loadPayload(op.savePayload()).orElseThrow());

            assertNull(loaded.currentCrystal());
            assertEquals(CrystalGenOp.BASE_PLACE_PROBABILITY, loaded.placeProbability(), EPSILON);
            assertEquals(op.typeId(), loaded.typeId());
        }
    }

    @Test
    void codecPreservesPlacedCrystalAndGrownProbability() {
        BlockPos crystal = new BlockPos(3, 5, 7);

        FireCrystalOp loaded = assertInstanceOf(FireCrystalOp.class,
                EntityPayload.loadPayload(new FireCrystalOp(crystal, 0.47).savePayload()).orElseThrow());

        assertEquals(crystal, loaded.currentCrystal());
        assertEquals(0.47, loaded.placeProbability(), EPSILON);
    }

    @Test
    void eachCrystalOpRegistersItsOwnTypeId() {
        assertEquals("gyromancy:fire_crystal", new FireCrystalOp().typeId().toString());
        assertEquals("gyromancy:water_crystal", new WaterCrystalOp().typeId().toString());
        assertEquals("gyromancy:wind_crystal", new WindCrystalOp().typeId().toString());
        assertEquals("gyromancy:earth_crystal", new EarthCrystalOp().typeId().toString());
    }
}