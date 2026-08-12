package com.astune.gyromancy.canvas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.UUID;

/** Persisted structural record for a canvas-local outer-circle AST. */
public record CanvasArrayRecord(
        UUID rootGlyph,
        List<UUID> boundGlyphs,
        long fingerprint,
        int color
) {
    private static final Codec<UUID> UUID_CODEC =
            Codec.STRING.xmap(UUID::fromString, UUID::toString);

    public static final Codec<CanvasArrayRecord> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    UUID_CODEC.fieldOf("root").forGetter(CanvasArrayRecord::rootGlyph),
                    UUID_CODEC.listOf().fieldOf("bound").forGetter(CanvasArrayRecord::boundGlyphs),
                    Codec.LONG.fieldOf("fingerprint").forGetter(CanvasArrayRecord::fingerprint),
                    Codec.INT.optionalFieldOf("color", 0xFFFFFFFF)
                            .forGetter(CanvasArrayRecord::color))
                    .apply(instance, CanvasArrayRecord::new));

    public CanvasArrayRecord {
        boundGlyphs = List.copyOf(boundGlyphs);
    }

    /** Source-compatible constructor for old call sites and legacy tests. */
    public CanvasArrayRecord(UUID rootGlyph, List<UUID> boundGlyphs, long fingerprint) {
        this(rootGlyph, boundGlyphs, fingerprint, 0xFFFFFFFF);
    }

    public static long fingerprint(UUID root, List<UUID> bound) {
        long value = 0xcbf29ce484222325L;
        value = mix(value, root.getMostSignificantBits());
        value = mix(value, root.getLeastSignificantBits());
        for (UUID uuid : bound) {
            value = mix(value, uuid.getMostSignificantBits());
            value = mix(value, uuid.getLeastSignificantBits());
        }
        return value;
    }

    private static long mix(long current, long next) {
        return (current ^ next) * 0x100000001b3L;
    }
}
