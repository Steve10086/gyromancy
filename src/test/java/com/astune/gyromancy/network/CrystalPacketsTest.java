package com.astune.gyromancy.network;

import com.astune.gyromancy.api.element.ElementType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrystalPacketsTest {
    @Test
    void spawnFxCodecPreservesPositionAndElement() {
        BlockPos pos = new BlockPos(-1234, 96, 5678);
        CrystalSpawnFxPacket packet = new CrystalSpawnFxPacket(pos, ElementType.LIGHT.ordinal());

        ByteBuf buffer = Unpooled.buffer();
        try {
            CrystalSpawnFxPacket.STREAM_CODEC.encode(buffer, packet);
            CrystalSpawnFxPacket decoded = CrystalSpawnFxPacket.STREAM_CODEC.decode(buffer);

            assertEquals(packet, decoded);
            assertEquals(pos, decoded.pos());
            assertEquals(ElementType.LIGHT, ElementType.byIndex(decoded.elementIndex()));
        } finally {
            buffer.release();
        }
    }

    @Test
    void growthCodecPreservesPosition() {
        BlockPos pos = new BlockPos(42, -32, 7);
        CrystalGrowthPacket packet = new CrystalGrowthPacket(pos);

        ByteBuf buffer = Unpooled.buffer();
        try {
            CrystalGrowthPacket.STREAM_CODEC.encode(buffer, packet);
            CrystalGrowthPacket decoded = CrystalGrowthPacket.STREAM_CODEC.decode(buffer);

            assertEquals(packet, decoded);
            assertEquals(pos, decoded.pos());
        } finally {
            buffer.release();
        }
    }
}
