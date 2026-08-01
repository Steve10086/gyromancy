package com.astune.gyromancy.network;

import com.astune.gyromancy.api.canvas.StampCanvasMaterial;
import com.astune.gyromancy.canvas.CanvasDocument;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StampCarvingPacketTest {
    @Test
    void snapshotCodecPreservesStampCanvasAndExternalMaterial() {
        int[] colors = new int[CanvasDocument.PIXELS_PER_BLOCK
                * CanvasDocument.PIXELS_PER_BLOCK];
        int[] effects = new int[colors.length];
        colors[17] = 0xFF345678;
        effects[17] = 4;
        CanvasDocument document = new CanvasDocument(
                1, 1, 1, colors, effects,
                java.util.List.of(), java.util.List.of());
        StampCanvasMaterial material = new StampCanvasMaterial(
                ResourceLocation.fromNamespaceAndPath(
                        "example", "textures/block/engraving.png"),
                0xFF102030,
                4);
        StampEditorSnapshotPacket packet = new StampEditorSnapshotPacket(
                InteractionHand.OFF_HAND, 12, document, material);

        ByteBuf buffer = Unpooled.buffer();
        try {
            StampEditorSnapshotPacket.STREAM_CODEC.encode(buffer, packet);
            StampEditorSnapshotPacket decoded =
                    StampEditorSnapshotPacket.STREAM_CODEC.decode(buffer);

            assertEquals(packet.hand(), decoded.hand());
            assertEquals(packet.revision(), decoded.revision());
            assertEquals(packet.document(), decoded.document());
            assertEquals(packet.material(), decoded.material());
        } finally {
            buffer.release();
        }
    }

    @Test
    void submissionCodecPreservesPrivateRaster() {
        int[] colors = new int[256];
        int[] effects = new int[256];
        colors[42] = 0xFFABCDEF;
        effects[42] = 9;
        SubmitStampCarvingPacket packet = new SubmitStampCarvingPacket(
                InteractionHand.MAIN_HAND, 3, 1, colors, effects);

        ByteBuf buffer = Unpooled.buffer();
        try {
            SubmitStampCarvingPacket.STREAM_CODEC.encode(buffer, packet);
            SubmitStampCarvingPacket decoded =
                    SubmitStampCarvingPacket.STREAM_CODEC.decode(buffer);

            assertEquals(packet.hand(), decoded.hand());
            assertEquals(packet.baseRevision(), decoded.baseRevision());
            assertEquals(packet.scale(), decoded.scale());
            assertArrayEquals(packet.colors(), decoded.colors());
            assertArrayEquals(packet.effects(), decoded.effects());
        } finally {
            buffer.release();
        }
    }
}
