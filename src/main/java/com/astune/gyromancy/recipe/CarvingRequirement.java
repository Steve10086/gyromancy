package com.astune.gyromancy.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Recursive structural requirement for one level of a carving glyph forest.
 *
 * <p>The rune list describes ungrouped glyphs at the current level. Nested
 * entries in {@code outer_circle} describe circle groups at that level. The
 * top-level requirement is not wrapped in an implicit root circle.</p>
 */
public record CarvingRequirement(
        List<ResourceLocation> runes,
        List<CarvingRequirement> outerCircle
) {
    public static final Codec<CarvingRequirement> CODEC = Codec.recursive(
            "carving_requirement",
            self -> RecordCodecBuilder.create(instance -> instance.group(
                    ResourceLocation.CODEC.listOf()
                            .optionalFieldOf("runes", List.of())
                            .forGetter(CarvingRequirement::runes),
                    self.listOf()
                            .optionalFieldOf("outer_circle", List.of())
                            .forGetter(CarvingRequirement::outerCircle))
                    .apply(instance, CarvingRequirement::new)));

    public CarvingRequirement {
        runes = List.copyOf(runes);
        outerCircle = List.copyOf(outerCircle);
    }
}
