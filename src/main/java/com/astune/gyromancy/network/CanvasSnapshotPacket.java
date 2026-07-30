package com.astune.gyromancy.network;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.client.canvas.CanvasClientState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CanvasSnapshotPacket(
        int entityId,
        int revision,
        CanvasDocument document,
        boolean openEditor
) implements CustomPacketPayload {
    public static final Type<CanvasSnapshotPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "canvas_snapshot"));

    public static final StreamCodec<ByteBuf, CanvasSnapshotPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CanvasSnapshotPacket decode(ByteBuf buf) {
            int entityId = buf.readInt();
            int revision = buf.readInt();
            boolean openEditor = buf.readBoolean();
            return new CanvasSnapshotPacket(entityId, revision,
                    CanvasPacketCodec.decodeDocument(buf), openEditor);
        }

        @Override
        public void encode(ByteBuf buf, CanvasSnapshotPacket packet) {
            buf.writeInt(packet.entityId);
            buf.writeInt(packet.revision);
            buf.writeBoolean(packet.openEditor);
            CanvasPacketCodec.encodeDocument(buf, packet.document);
        }
    };

    public static void handleClient(CanvasSnapshotPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> CanvasClientState.accept(packet));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
