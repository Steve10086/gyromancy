package com.astune.gyromancy.api.symbol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

/**
 * A successfully recognized glyph with its world position.
 * Stored in MagicArrayManager for later retrieval (e.g., by Phase 5 compilation).
 */
public record PositionedGlyph(
        UUID glyphUuid,
        int glyphId,
        ResourceLocation symbolId,
        float confidence,
        SymbolRole role,
        BlockPos worldPos,
        double minWorldX, double maxWorldX,
        double minWorldY, double maxWorldY,
        Set<PixelPos> pixels
) {
    public static final Codec<PositionedGlyph> CODEC = RecordCodecBuilder.create(i ->
            i.group(Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("uuid").forGetter(PositionedGlyph::glyphUuid),
                    Codec.INT.fieldOf("glyph_id").forGetter(PositionedGlyph::glyphId),
                    ResourceLocation.CODEC.fieldOf("symbol_id").forGetter(PositionedGlyph::symbolId),
                    Codec.FLOAT.fieldOf("confidence").forGetter(PositionedGlyph::confidence),
                    SymbolRole.CODEC.fieldOf("role").forGetter(PositionedGlyph::role),
                    BlockPos.CODEC.fieldOf("world_pos").forGetter(PositionedGlyph::worldPos),
                    Codec.DOUBLE.fieldOf("min_world_x").forGetter(PositionedGlyph::minWorldX),
                    Codec.DOUBLE.fieldOf("max_world_x").forGetter(PositionedGlyph::maxWorldX),
                    Codec.DOUBLE.fieldOf("min_world_y").forGetter(PositionedGlyph::minWorldY),
                    Codec.DOUBLE.fieldOf("max_world_y").forGetter(PositionedGlyph::maxWorldY),
                    PixelPos.CODEC.listOf().xmap(Set::copyOf, java.util.List::copyOf).fieldOf("pixels").forGetter(PositionedGlyph::pixels))
             .apply(i, PositionedGlyph::new));

    public PositionedGlyph withGlyphId(int newGlyphId) {
        return new PositionedGlyph(glyphUuid, newGlyphId, symbolId, confidence, role, worldPos,
                minWorldX, maxWorldX, minWorldY, maxWorldY, pixels);
    }

    public record Entry(UUID uuid, PositionedGlyph glyph) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i ->
                i.group(Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("uuid").forGetter(Entry::uuid),
                        PositionedGlyph.CODEC.fieldOf("glyph").forGetter(Entry::glyph))
                 .apply(i, Entry::new));
    }

    public static Codec<HashMap<UUID, PositionedGlyph>> mapCodec() {
        return Entry.CODEC.listOf().xmap(
                l -> {
                    var m = new HashMap<UUID, PositionedGlyph>();
                    for (var e : l) m.put(e.uuid(), e.glyph());
                    return m;
                },
                m -> m.entrySet().stream().map(e -> new Entry(e.getKey(), e.getValue())).toList());
    }
}
