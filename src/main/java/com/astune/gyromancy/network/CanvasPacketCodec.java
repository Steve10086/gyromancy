package com.astune.gyromancy.network;

import com.astune.gyromancy.canvas.CanvasArrayRecord;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.canvas.CanvasGlyph;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

final class CanvasPacketCodec {
    /** Guard against malformed packets claiming an absurd compile cache size. */
    private static final int MAX_COMPILE_ENTRIES = 4096;

    private static final StreamCodec<ByteBuf, CanvasGlyph> GLYPH_STREAM_CODEC =
            ByteBufCodecs.fromCodec(CanvasGlyph.CODEC);
    private static final StreamCodec<ByteBuf, CanvasArrayRecord> ARRAY_STREAM_CODEC =
            ByteBufCodecs.fromCodec(CanvasArrayRecord.CODEC);

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
        // The recognized structure travels with the raster so a client that
        // picks the canvas can place a working copy of it.
        buf.writeInt(document.glyphs().size());
        for (CanvasGlyph glyph : document.glyphs()) GLYPH_STREAM_CODEC.encode(buf, glyph);
        buf.writeInt(document.arrays().size());
        for (CanvasArrayRecord array : document.arrays()) ARRAY_STREAM_CODEC.encode(buf, array);
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
        List<CanvasGlyph> glyphs = decodeEntries(buf, GLYPH_STREAM_CODEC);
        List<CanvasArrayRecord> arrays = decodeEntries(buf, ARRAY_STREAM_CODEC);
        return new CanvasDocument(physicalWidth, physicalHeight, scale,
                colors, effects, glyphs, arrays);
    }

    private static <T> List<T> decodeEntries(ByteBuf buf, StreamCodec<ByteBuf, T> codec) {
        int count = buf.readInt();
        if (count < 0 || count > MAX_COMPILE_ENTRIES) {
            throw new IllegalArgumentException("Invalid canvas compile cache size");
        }
        List<T> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) entries.add(codec.decode(buf));
        return List.copyOf(entries);
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
