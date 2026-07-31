package com.astune.gyromancy.element;

import com.astune.gyromancy.api.element.ElementConcentrations;
import com.astune.gyromancy.api.element.ElementType;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElementTileTest {

    @Test
    void localIndexCoversEveryCellExactlyOnce() {
        boolean[] seen = new boolean[ElementTile.CELL_COUNT];
        for (int y = 0; y < ElementTile.EDGE; y++) {
            for (int z = 0; z < ElementTile.EDGE; z++) {
                for (int x = 0; x < ElementTile.EDGE; x++) {
                    int index = ElementTile.index(x, y, z);
                    assertFalse(seen[index]);
                    seen[index] = true;
                    assertEquals(x, ElementTile.localX(index));
                    assertEquals(y, ElementTile.localY(index));
                    assertEquals(z, ElementTile.localZ(index));
                }
            }
        }
        for (boolean value : seen) assertTrue(value);
    }

    @Test
    void chunkDataHandlesNegativeCoordinatesAndHeight() {
        ElementChunkData data = new ElementChunkData();
        BlockPos pos = new BlockPos(-1, -9, -16);
        long[] values = new long[ElementType.COUNT];
        values[ElementType.MANA.ordinal()] = 1234L;

        data.set(pos, ElementConcentrations.ofValues(values));

        assertTrue(data.has(pos));
        assertEquals(1234L, data.get(pos).get(ElementType.MANA));
        assertEquals(-2, ElementChunkData.tileY(ElementChunkData.tileKey(pos)));
        assertEquals(1, ElementChunkData.localTileX(ElementChunkData.tileKey(pos)));
        assertEquals(0, ElementChunkData.localTileZ(ElementChunkData.tileKey(pos)));
    }

    @Test
    void tileCodecRoundTripsActiveCells() {
        ElementChunkData original = new ElementChunkData();
        BlockPos first = new BlockPos(1, 70, 2);
        BlockPos second = new BlockPos(9, -3, 15);
        original.set(first, ElementConcentrations.uniform(42L));

        long[] secondValues = new long[ElementType.COUNT];
        secondValues[ElementType.FIRE.ordinal()] = 900L;
        original.set(second, ElementConcentrations.ofValues(secondValues));

        JsonElement encoded = ElementChunkData.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow();
        ElementChunkData decoded = ElementChunkData.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow();

        assertEquals(2, decoded.activeCellCount());
        assertEquals(42L, decoded.get(first).get(ElementType.WIND));
        assertEquals(900L, decoded.get(second).get(ElementType.FIRE));
    }

    @Test
    void codecMigratesLegacyPositionMap() {
        BlockPos pos = new BlockPos(7, 12, 5);
        long[] values = new long[ElementType.COUNT];
        values[ElementType.WATER.ordinal()] = 777L;
        HashMap<BlockPos, ElementConcentrations> legacy = new HashMap<>();
        legacy.put(pos, ElementConcentrations.ofValues(values));

        JsonElement encodedLegacy = ElementConcentrations.mapCodec()
                .encodeStart(JsonOps.INSTANCE, legacy)
                .getOrThrow();
        ElementChunkData migrated = ElementChunkData.CODEC
                .parse(JsonOps.INSTANCE, encodedLegacy)
                .getOrThrow();

        assertNotNull(migrated.get(pos));
        assertEquals(777L, migrated.get(pos).get(ElementType.WATER));
    }

    @Test
    void sixFaceFlowIsAntisymmetricAndStableForIsolatedPeak() {
        long forward = ElementChunkProcessor.computeFaceFlow(700L, 0L);
        long reverse = ElementChunkProcessor.computeFaceFlow(0L, 700L);

        assertEquals(100L, forward);
        assertEquals(-forward, reverse);
        assertEquals(100L, 700L - 6L * forward);
    }
}
