package com.astune.gyromancy.wand;

import com.astune.gyromancy.canvas.CanvasDocument;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/** A wand-owned combined material and compile cache for one projection slot. */
public record WandSlotSnapshot(Optional<CanvasDocument> document, long sourceFingerprint) {
    public static final WandSlotSnapshot EMPTY = new WandSlotSnapshot(Optional.empty(), 0L);

    public static final Codec<WandSlotSnapshot> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    CanvasDocument.CODEC.optionalFieldOf("document")
                            .forGetter(WandSlotSnapshot::document),
                    Codec.LONG.optionalFieldOf("source_fingerprint", 0L)
                            .forGetter(WandSlotSnapshot::sourceFingerprint))
                    .apply(instance, WandSlotSnapshot::new));

    public WandSlotSnapshot {
        document = document == null ? Optional.empty() : document;
    }

    public WandSlotSnapshot(Optional<CanvasDocument> document) {
        this(document, document.map(WandSlotCompiler::rasterFingerprint).orElse(0L));
    }

    public static WandSlotSnapshot of(CanvasDocument document) {
        return new WandSlotSnapshot(Optional.of(document),
                WandSlotCompiler.rasterFingerprint(document));
    }

    public static WandSlotSnapshot of(CanvasDocument document, long sourceFingerprint) {
        return new WandSlotSnapshot(Optional.of(document), sourceFingerprint);
    }
}
