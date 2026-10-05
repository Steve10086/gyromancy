package com.astune.gyromancy.network;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.client.entity.FireballRenderer;
import com.astune.gyromancy.entity.ball.FireballEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.world.phys.Vec3;

/** Tells nearby clients which terminal visual state a fireball entered. */
public record FireballStateEventPacket(
        int entityId,
        boolean explosion,
        Vec3 position,
        float ballSize,
        double averageElementLevel
) implements CustomPacketPayload {
    public static final Type<FireballStateEventPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "fireball_state_event"));

    public static final StreamCodec<ByteBuf, FireballStateEventPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FireballStateEventPacket decode(ByteBuf buf) {
            return new FireballStateEventPacket(
                    buf.readInt(),
                    buf.readBoolean(),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    buf.readFloat(),
                    buf.readDouble());
        }

        @Override
        public void encode(ByteBuf buf, FireballStateEventPacket packet) {
            buf.writeInt(packet.entityId());
            buf.writeBoolean(packet.explosion());
            buf.writeDouble(packet.position().x);
            buf.writeDouble(packet.position().y);
            buf.writeDouble(packet.position().z);
            buf.writeFloat(packet.ballSize());
            buf.writeDouble(packet.averageElementLevel());
        }
    };

    public static void broadcast(FireballEntity entity, boolean explosion) {
        if (entity.level().isClientSide || entity.isRemoved()) return;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new FireballStateEventPacket(
                entity.getId(), explosion, entity.position(), entity.getBallSize(),
                entity.getAverageElementLevel()));
    }

    public String eventName() {
        return explosion ? "explosion" : "discard";
    }

    public static void handleClient(FireballStateEventPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> FireballRenderer.onStateEvent(packet));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
