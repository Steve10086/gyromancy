package com.astune.gyromancy.network;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.client.effect.InstantSpellEffects;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Plays a one-shot instant spell effect at a fixed world position with a fixed
 * radius and element tint.
 */
public record InstantSpellFxPacket(Vec3 position, float radius, int elementIndex)
        implements CustomPacketPayload {
    public static final Type<InstantSpellFxPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "instant_spell_fx"));
    private static final double VIEW_RANGE_PADDING = 32.0;

    public static final StreamCodec<ByteBuf, InstantSpellFxPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public InstantSpellFxPacket decode(ByteBuf buf) {
            return new InstantSpellFxPacket(
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    buf.readFloat(), buf.readUnsignedByte());
        }

        @Override
        public void encode(ByteBuf buf, InstantSpellFxPacket packet) {
            buf.writeDouble(packet.position().x);
            buf.writeDouble(packet.position().y);
            buf.writeDouble(packet.position().z);
            buf.writeFloat(packet.radius());
            buf.writeByte(packet.elementIndex());
        }
    };

    /** Sends the notice to every player near the effect's position. */
    public static void broadcast(ServerLevel level, Vec3 position, float radius, ElementType element) {
        PacketDistributor.sendToPlayersNear(level, null, position.x, position.y, position.z,
                radius * 2.0 + VIEW_RANGE_PADDING,
                new InstantSpellFxPacket(position, radius, element.ordinal()));
    }

    public static void handleClient(InstantSpellFxPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> InstantSpellEffects.onFx(
                packet.position(), packet.radius(), packet.elementIndex()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
