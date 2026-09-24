package com.astune.gyromancy.network;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.canvas.CanvasToolSettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Persists a player's canvas editor preferences on the authoritative side. */
public record UpdateCanvasToolSettingsPacket(int penDiameter, double stampScale)
        implements CustomPacketPayload {
    public static final Type<UpdateCanvasToolSettingsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    Gyromancy.MODID, "update_canvas_tool_settings"));

    public static final StreamCodec<ByteBuf, UpdateCanvasToolSettingsPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public UpdateCanvasToolSettingsPacket decode(ByteBuf buf) {
                    return new UpdateCanvasToolSettingsPacket(
                            buf.readInt(), buf.readDouble());
                }

                @Override
                public void encode(ByteBuf buf, UpdateCanvasToolSettingsPacket packet) {
                    buf.writeInt(packet.penDiameter());
                    buf.writeDouble(packet.stampScale());
                }
            };

    public static void handleServer(UpdateCanvasToolSettingsPacket packet,
                                    IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        CanvasToolSettings.applyFromClient(player, packet.penDiameter(), packet.stampScale());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
