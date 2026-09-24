package com.astune.gyromancy.canvas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Persisted per-player canvas editor preferences.
 *
 * <p>The canonical constructor clamps every field, so values restored from
 * disk or received from a client can never leave their supported ranges.
 */
public record CanvasToolSettingsData(int penDiameter, double stampScale) {
    public static final CanvasToolSettingsData DEFAULT = new CanvasToolSettingsData(
            CanvasToolSettings.DEFAULT_PEN_DIAMETER, CanvasToolSettings.DEFAULT_STAMP_SCALE);

    public static final Codec<CanvasToolSettingsData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("pen_diameter",
                                    CanvasToolSettings.DEFAULT_PEN_DIAMETER)
                            .forGetter(CanvasToolSettingsData::penDiameter),
                    Codec.DOUBLE.optionalFieldOf("stamp_scale",
                                    CanvasToolSettings.DEFAULT_STAMP_SCALE)
                            .forGetter(CanvasToolSettingsData::stampScale))
                    .apply(instance, CanvasToolSettingsData::new));

    public CanvasToolSettingsData {
        penDiameter = CanvasToolSettings.clampPenDiameter(penDiameter);
        stampScale = CanvasToolSettings.clampStampScale(stampScale);
    }

    public CanvasToolSettingsData withPenDiameter(int value) {
        return new CanvasToolSettingsData(value, stampScale);
    }

    public CanvasToolSettingsData withStampScale(double value) {
        return new CanvasToolSettingsData(penDiameter, value);
    }
}
