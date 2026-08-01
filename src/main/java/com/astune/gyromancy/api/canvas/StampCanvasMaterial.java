package com.astune.gyromancy.api.canvas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/**
 * Externally supplied appearance and mark properties for a stamp canvas.
 *
 * <p>The texture is rendered as the carving surface. The mark color and
 * effect are written into the stamp's private canvas wherever the player
 * carves. Other mods may replace this value through the stamp material data
 * component without coupling the editor to their item or block types.
 */
public record StampCanvasMaterial(
        ResourceLocation texture,
        int markColor,
        int markEffect
) {
    public static final StampCanvasMaterial DEFAULT = new StampCanvasMaterial(
            ResourceLocation.withDefaultNamespace("textures/block/stone.png"),
            0xFF3A3532,
            1);

    public static final Codec<StampCanvasMaterial> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("texture")
                            .forGetter(StampCanvasMaterial::texture),
                    Codec.INT.fieldOf("mark_color")
                            .forGetter(StampCanvasMaterial::markColor),
                    Codec.intRange(1, 255).fieldOf("mark_effect")
                            .forGetter(StampCanvasMaterial::markEffect))
                    .apply(instance, StampCanvasMaterial::new));

    public StampCanvasMaterial {
        if (markEffect < 1 || markEffect > 255) {
            throw new IllegalArgumentException(
                    "Stamp material effect must be between 1 and 255");
        }
    }
}
