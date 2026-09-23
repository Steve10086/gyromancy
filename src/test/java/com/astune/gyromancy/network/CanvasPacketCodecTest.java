package com.astune.gyromancy.network;

import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.canvas.CanvasArrayRecord;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.canvas.CanvasGlyph;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanvasPacketCodecTest {
    @Test
    void documentRoundTripKeepsRecognizedStructure() {
        CanvasGlyph glyph = new CanvasGlyph(
                UUID.fromString("00000000-0000-0000-0000-0000000000ab"),
                ResourceLocation.fromNamespaceAndPath("gyromancy", "circle_outer"),
                0.9F,
                SymbolRole.OUTER_CIRCLE,
                1.0, 0.0, 0.5, 0.5,
                0.1, 0.9, 0.1, 0.9,
                new int[]{1, 2, 3});
        CanvasArrayRecord array = new CanvasArrayRecord(
                glyph.glyphUuid(), List.of(glyph.glyphUuid()),
                CanvasArrayRecord.fingerprint(glyph.glyphUuid(), List.of(glyph.glyphUuid())),
                0xFF5A7BC1);
        CanvasDocument document = CanvasDocument.blank(2, 2)
                .withCompileCache(List.of(glyph), List.of(array));

        ByteBuf buffer = Unpooled.buffer();
        try {
            CanvasPacketCodec.encodeDocument(buffer, document);
            CanvasDocument decoded = CanvasPacketCodec.decodeDocument(buffer);

            assertEquals(document, decoded);
            assertEquals(1, decoded.glyphs().size());
            assertEquals(1, decoded.arrays().size());
        } finally {
            buffer.release();
        }
    }

    @Test
    void blankDocumentRoundTripStaysBlank() {
        CanvasDocument document = CanvasDocument.blank(3, 2);

        ByteBuf buffer = Unpooled.buffer();
        try {
            CanvasPacketCodec.encodeDocument(buffer, document);
            CanvasDocument decoded = CanvasPacketCodec.decodeDocument(buffer);

            assertEquals(document, decoded);
        } finally {
            buffer.release();
        }
    }
}
