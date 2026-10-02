package com.astune.gyromancy.network;

import com.astune.gyromancy.api.element.ElementType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstantSpellFxPacketTest {

    @Test
    void codecPreservesPositionRadiusAndElement() {
        InstantSpellFxPacket packet = new InstantSpellFxPacket(
                new Vec3(1.5, -2.25, 300.125), 3.5F, ElementType.EARTH.ordinal());

        ByteBuf buffer = Unpooled.buffer();
        try {
            InstantSpellFxPacket.STREAM_CODEC.encode(buffer, packet);
            InstantSpellFxPacket decoded = InstantSpellFxPacket.STREAM_CODEC.decode(buffer);

            assertEquals(packet, decoded);
            assertEquals(3.5F, decoded.radius());
            assertEquals(ElementType.EARTH, ElementType.byIndex(decoded.elementIndex()));
        } finally {
            buffer.release();
        }
    }
}
