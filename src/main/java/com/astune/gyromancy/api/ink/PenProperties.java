package com.astune.gyromancy.api.ink;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * Defines the properties of a pen/brush used for drawing on canvas blocks.
 * Stored as a DataComponent on pen items.
 */
public record PenProperties(
        float brushSize,        // Default: 1.0 (pixel width of the brush)
        int inkCapacity,        // Max uses before needing a refill
        float precision,        // Affects symbol recognition quality [0.0-1.0], 1.0 = perfect
        int drawingSpeed        // Ticks between paint actions (lower = faster drawing)
) {
    public static final PenProperties DEFAULT = new PenProperties(1/16.0f, 64, 1.0f, 2);

    public static final Codec<PenProperties> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.FLOAT.optionalFieldOf("brushSize").forGetter(p -> Optional.of(p.brushSize)),
                    Codec.INT.optionalFieldOf("inkCapacity").forGetter(p -> Optional.of(p.inkCapacity)),
                    Codec.FLOAT.optionalFieldOf("precision").forGetter(p -> Optional.of(p.precision)),
                    Codec.INT.optionalFieldOf("drawingSpeed").forGetter(p -> Optional.of(p.drawingSpeed))
            ).apply(instance, (bs, ic, p, ds) -> new PenProperties(
                    bs.orElse(1.0f), ic.orElse(64), p.orElse(1.0f), ds.orElse(2)
            ))
    );

    /** Returns a copy with a different brush size */
    public PenProperties withBrushSize(float brushSize) {
        return new PenProperties(Math.max(0.1f, brushSize), inkCapacity, precision, drawingSpeed);
    }

    /** Returns a copy with modified ink capacity */
    public PenProperties withInkCapacity(int inkCapacity) {
        return new PenProperties(brushSize, Math.max(1, inkCapacity), precision, drawingSpeed);
    }
}
