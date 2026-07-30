package com.astune.gyromancy.network;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.canvas.CanvasCompileService;
import com.astune.gyromancy.canvas.CanvasEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SubmitCanvasEditPacket(
        int entityId,
        int baseRevision,
        int scale,
        int[] colors,
        int[] effects
) implements CustomPacketPayload {
    public static final Type<SubmitCanvasEditPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "submit_canvas_edit"));

    public static final StreamCodec<ByteBuf, SubmitCanvasEditPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SubmitCanvasEditPacket decode(ByteBuf buf) {
            int entityId = buf.readInt();
            int revision = buf.readInt();
            CanvasPacketCodec.Raster raster = CanvasPacketCodec.decodeRaster(buf);
            return new SubmitCanvasEditPacket(entityId, revision, raster.scale(),
                    raster.colors(), raster.effects());
        }

        @Override
        public void encode(ByteBuf buf, SubmitCanvasEditPacket packet) {
            buf.writeInt(packet.entityId);
            buf.writeInt(packet.baseRevision);
            CanvasPacketCodec.encodeRaster(buf, packet.scale, packet.colors, packet.effects);
        }
    };

    public static void handleServer(SubmitCanvasEditPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!(level.getEntity(packet.entityId) instanceof CanvasEntity canvas)) return;
        if (player.distanceToSqr(canvas) > 64.0) return;

        boolean accepted = CanvasCompileService.applyEditorSubmission(
                level, canvas, packet.baseRevision, packet.scale,
                packet.colors, packet.effects);
        if (!accepted) canvas.sendSnapshot(player, true);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
