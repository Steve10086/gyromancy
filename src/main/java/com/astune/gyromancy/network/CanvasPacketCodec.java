package com.astune.gyromancy.network;

import com.astune.gyromancy.canvas.CanvasDocument;
import io.netty.buffer.ByteBuf;

final class CanvasPacketCodec {
    private CanvasPacketCodec() {}

    static void encodeDocument(ByteBuf buf, CanvasDocument document) {
        buf.writeByte(document.physicalWidth());
        buf.writeByte(document.physicalHeight());
        buf.writeByte(document.resolutionScale());
        int[] colors = document.colors();
        int[] effects = document.strokeEffects();
        buf.writeInt(colors.length);
        for (int color : colors) buf.writeInt(color);
        for (int effect : effects) buf.writeInt(effect);
    }

    static CanvasDocument decodeDocument(ByteBuf buf) {
        int physicalWidth = buf.readUnsignedByte();
        int physicalHeight = buf.readUnsignedByte();
        int scale = buf.readUnsignedByte();
        int length = buf.readInt();
        if (length < 0 || length > CanvasDocument.MAX_RESOLUTION * CanvasDocument.MAX_RESOLUTION) {
            throw new IllegalArgumentException("Invalid canvas raster length");
        }
        int[] colors = new int[length];
        int[] effects = new int[length];
        for (int index = 0; index < length; index++) colors[index] = buf.readInt();
        for (int index = 0; index < length; index++) effects[index] = buf.readInt();
        return new CanvasDocument(physicalWidth, physicalHeight, scale,
                colors, effects, java.util.List.of(), java.util.List.of());
    }

    static void encodeRaster(ByteBuf buf, int scale, int[] colors, int[] effects) {
        buf.writeByte(scale);
        buf.writeInt(colors.length);
        for (int color : colors) buf.writeInt(color);
        for (int effect : effects) buf.writeInt(effect);
    }

    static Raster decodeRaster(ByteBuf buf) {
        int scale = buf.readUnsignedByte();
        int length = buf.readInt();
        if (length < 0 || length > CanvasDocument.MAX_RESOLUTION * CanvasDocument.MAX_RESOLUTION) {
            throw new IllegalArgumentException("Invalid canvas raster length");
        }
        int[] colors = new int[length];
        int[] effects = new int[length];
        for (int index = 0; index < length; index++) colors[index] = buf.readInt();
        for (int index = 0; index < length; index++) effects[index] = buf.readInt();
        return new Raster(scale, colors, effects);
    }

    record Raster(int scale, int[] colors, int[] effects) {}
}
