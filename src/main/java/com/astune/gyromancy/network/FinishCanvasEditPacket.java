package com.astune.gyromancy.network;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.canvas.CanvasEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Completes an editor session and activates a canvas that was placed as a scroll. */
public record FinishCanvasEditPacket(int entityId) implements CustomPacketPayload {
    public static final Type<FinishCanvasEditPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "finish_canvas_edit"));

    public static final StreamCodec<ByteBuf, FinishCanvasEditPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FinishCanvasEditPacket decode(ByteBuf buf) {
            return new FinishCanvasEditPacket(buf.readInt());
        }

        @Override
        public void encode(ByteBuf buf, FinishCanvasEditPacket packet) {
            buf.writeInt(packet.entityId);
        }
    };

    public static void handleServer(FinishCanvasEditPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || !(level.getEntity(packet.entityId) instanceof CanvasEntity canvas)
                || player.distanceToSqr(canvas) > 64.0) {
            return;
        }
        canvas.unfurl();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
