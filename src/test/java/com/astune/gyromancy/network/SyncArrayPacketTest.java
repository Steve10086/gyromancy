package com.astune.gyromancy.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SyncArrayPacketTest {
    @Test
    void codecPreservesPerArrayCompilationEffectLifetime() {
        SyncArrayPacket.BlockData part = new SyncArrayPacket.BlockData(
                new com.astune.gyromancy.api.geometry.SurfaceFrame(
                        new Vec3(1.0, 2.0, 3.0),
                        new Vec3(1.0, 0.0, 0.0),
                        new Vec3(0.0, 1.0, 0.0),
                        new Vec3(0.0, 0.0, 1.0)),
                new Vec3(1.0, 2.0, 3.0),
                Direction.NORTH,
                new Vec3(1.0, 0.0, 0.0),
                new Vec3(0.0, 1.0, 0.0),
                2, 2, new byte[]{1, 0, 0, 1}, 42);
        SyncArrayPacket.ArrayData array = new SyncArrayPacket.ArrayData(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                0xFF336699,
                73,
                1_234_567L,
                List.of(part));
        ByteBuf buffer = Unpooled.buffer();
        try {
            SyncArrayPacket.STREAM_CODEC.encode(
                    buffer, new SyncArrayPacket(List.of(array)));

            SyncArrayPacket decoded =
                    SyncArrayPacket.STREAM_CODEC.decode(buffer);
            SyncArrayPacket.ArrayData decodedArray = decoded.arrays().getFirst();
            SyncArrayPacket.BlockData decodedPart =
                    decodedArray.parts().getFirst();

            assertEquals(array.id(), decodedArray.id());
            assertEquals(array.color(), decodedArray.color());
            assertEquals(73, decodedArray.compilationEffectTicks());
            assertEquals(1_234_567L, decodedArray.compilationEffectEndTick());
            assertEquals(part.center(), decodedPart.center());
            assertEquals(part.face(), decodedPart.face());
            assertEquals(part.surface(), decodedPart.surface());
            assertEquals(42, decodedPart.sourceEntityId());
            assertArrayEquals(part.mask(), decodedPart.mask());
        } finally {
            buffer.release();
        }
    }
}
