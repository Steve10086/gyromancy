package com.astune.gyromancy.network;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.client.ElementDebugRenderer;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Full-snapshot sync for the server-maintained recognized glyph list. */
public record SyncGlyphPacket(List<GlyphData> glyphs) implements CustomPacketPayload {

    public static final Type<SyncGlyphPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "sync_glyphs"));

    public static final StreamCodec<ByteBuf, SyncGlyphPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncGlyphPacket decode(ByteBuf buf) {
            int count = buf.readInt();
            List<GlyphData> glyphs = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int glyphId = buf.readInt();
                ResourceLocation symbolId = ResourceLocation.STREAM_CODEC.decode(buf);
                float confidence = buf.readFloat();
                BlockPos samplePos = BlockPos.STREAM_CODEC.decode(buf);
                Direction face = Direction.values()[buf.readUnsignedByte() % Direction.values().length];
                double minWorldX = buf.readDouble();
                double maxWorldX = buf.readDouble();
                double minWorldY = buf.readDouble();
                double maxWorldY = buf.readDouble();
                glyphs.add(new GlyphData(glyphId, symbolId, confidence, samplePos, face,
                        minWorldX, maxWorldX, minWorldY, maxWorldY));
            }
            return new SyncGlyphPacket(glyphs);
        }

        @Override
        public void encode(ByteBuf buf, SyncGlyphPacket packet) {
            buf.writeInt(packet.glyphs.size());
            for (GlyphData glyph : packet.glyphs) {
                buf.writeInt(glyph.glyphId());
                ResourceLocation.STREAM_CODEC.encode(buf, glyph.symbolId());
                buf.writeFloat(glyph.confidence());
                BlockPos.STREAM_CODEC.encode(buf, glyph.samplePos());
                buf.writeByte(glyph.face().ordinal());
                buf.writeDouble(glyph.minWorldX());
                buf.writeDouble(glyph.maxWorldX());
                buf.writeDouble(glyph.minWorldY());
                buf.writeDouble(glyph.maxWorldY());
            }
        }
    };

    public static void handleClient(SyncGlyphPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ElementDebugRenderer.replaceGlyphData(packet.glyphs()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record GlyphData(
            int glyphId,
            ResourceLocation symbolId,
            float confidence,
            BlockPos samplePos,
            Direction face,
            double minWorldX,
            double maxWorldX,
            double minWorldY,
            double maxWorldY
    ) {}
}
