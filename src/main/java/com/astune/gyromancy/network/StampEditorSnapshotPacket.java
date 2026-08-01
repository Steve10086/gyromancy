package com.astune.gyromancy.network;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.canvas.StampCanvasMaterial;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.client.canvas.StampCarvingScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Opens an entity-independent carving editor from an authoritative stamp snapshot. */
public record StampEditorSnapshotPacket(
        InteractionHand hand,
        int revision,
        CanvasDocument document,
        StampCanvasMaterial material
) implements CustomPacketPayload {
    public static final Type<StampEditorSnapshotPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Gyromancy.MODID, "stamp_editor_snapshot"));

    public static final StreamCodec<ByteBuf, StampEditorSnapshotPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public StampEditorSnapshotPacket decode(ByteBuf buffer) {
                    InteractionHand hand = buffer.readBoolean()
                            ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                    int revision = buffer.readInt();
                    CanvasDocument document = CanvasPacketCodec.decodeDocument(buffer);
                    ResourceLocation texture = ResourceLocation.STREAM_CODEC.decode(buffer);
                    int markColor = buffer.readInt();
                    int markEffect = buffer.readUnsignedByte();
                    return new StampEditorSnapshotPacket(
                            hand, revision, document,
                            new StampCanvasMaterial(texture, markColor, markEffect));
                }

                @Override
                public void encode(ByteBuf buffer, StampEditorSnapshotPacket packet) {
                    buffer.writeBoolean(packet.hand == InteractionHand.OFF_HAND);
                    buffer.writeInt(packet.revision);
                    CanvasPacketCodec.encodeDocument(buffer, packet.document);
                    ResourceLocation.STREAM_CODEC.encode(buffer, packet.material.texture());
                    buffer.writeInt(packet.material.markColor());
                    buffer.writeByte(packet.material.markEffect());
                }
            };

    public static void handleClient(StampEditorSnapshotPacket packet,
                                    IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(
                new StampCarvingScreen(
                        packet.hand, packet.revision, packet.document, packet.material)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
