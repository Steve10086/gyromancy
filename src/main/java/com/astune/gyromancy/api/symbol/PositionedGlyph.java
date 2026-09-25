package com.astune.gyromancy.api.symbol;

import com.astune.gyromancy.api.element.ManaElements;
import com.astune.gyromancy.api.geometry.SurfaceFrame;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Optional;
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
        Vec3 front,
        double length,
        double width,
        BlockPos worldPos,
        double minWorldX, double maxWorldX,
        double minWorldY, double maxWorldY,
        Set<PixelPos> pixels,
        Optional<UUID> sourceCanvasId,
        SurfaceFrame surface,
        ManaElements manaElements
) {
    private static final Codec<UUID> UUID_CODEC =
            Codec.STRING.xmap(UUID::fromString, UUID::toString);
    private static final Codec<Vec3> VEC3_CODEC = RecordCodecBuilder.create(i ->
            i.group(Codec.DOUBLE.fieldOf("x").forGetter(v -> v.x),
                    Codec.DOUBLE.fieldOf("y").forGetter(v -> v.y),
                    Codec.DOUBLE.fieldOf("z").forGetter(v -> v.z))
             .apply(i, Vec3::new));

    private record Tail(Optional<UUID> sourceCanvasId, Optional<SurfaceFrame> surface,
                        ManaElements manaElements) {}

    private static final MapCodec<Tail> TAIL_CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    UUID_CODEC.optionalFieldOf("source_canvas").forGetter(Tail::sourceCanvasId),
                    SurfaceFrame.CODEC.optionalFieldOf("surface").forGetter(Tail::surface),
                    ManaElements.CODEC.optionalFieldOf("mana_elements", ManaElements.EMPTY)
                            .forGetter(Tail::manaElements))
                    .apply(instance, Tail::new));

    public static final Codec<PositionedGlyph> CODEC = RecordCodecBuilder.create(i ->
            i.group(UUID_CODEC.fieldOf("uuid").forGetter(PositionedGlyph::glyphUuid),
                    Codec.INT.fieldOf("glyph_id").forGetter(PositionedGlyph::glyphId),
                    ResourceLocation.CODEC.fieldOf("symbol_id").forGetter(PositionedGlyph::symbolId),
                    Codec.FLOAT.fieldOf("confidence").forGetter(PositionedGlyph::confidence),
                    SymbolRole.CODEC.fieldOf("role").forGetter(PositionedGlyph::role),
                    VEC3_CODEC.optionalFieldOf("front", Vec3.ZERO).forGetter(PositionedGlyph::front),
                    Codec.DOUBLE.optionalFieldOf("length", 0.0).forGetter(PositionedGlyph::length),
                    Codec.DOUBLE.optionalFieldOf("width", 0.0).forGetter(PositionedGlyph::width),
                    BlockPos.CODEC.fieldOf("world_pos").forGetter(PositionedGlyph::worldPos),
                    Codec.DOUBLE.fieldOf("min_world_x").forGetter(PositionedGlyph::minWorldX),
                    Codec.DOUBLE.fieldOf("max_world_x").forGetter(PositionedGlyph::maxWorldX),
                    Codec.DOUBLE.fieldOf("min_world_y").forGetter(PositionedGlyph::minWorldY),
                    Codec.DOUBLE.fieldOf("max_world_y").forGetter(PositionedGlyph::maxWorldY),
                    PixelPos.CODEC.listOf().xmap(Set::copyOf, java.util.List::copyOf).fieldOf("pixels").forGetter(PositionedGlyph::pixels),
                    TAIL_CODEC.forGetter(glyph -> new Tail(
                            glyph.sourceCanvasId(),
                            Optional.of(glyph.surface()),
                            glyph.manaElements())))
             .apply(i, PositionedGlyph::fromCodec));

    public PositionedGlyph {
        pixels = Set.copyOf(pixels);
        sourceCanvasId = sourceCanvasId == null ? Optional.empty() : sourceCanvasId;
        if (surface == null) surface = inferLegacySurface(worldPos, pixels);
        manaElements = manaElements == null ? ManaElements.EMPTY : manaElements;
    }

    private static PositionedGlyph fromCodec(
            UUID glyphUuid, int glyphId, ResourceLocation symbolId, float confidence,
            SymbolRole role, Vec3 front, double length, double width, BlockPos worldPos,
            double minWorldX, double maxWorldX, double minWorldY, double maxWorldY,
            Set<PixelPos> pixels, Tail tail) {
        return new PositionedGlyph(glyphUuid, glyphId, symbolId, confidence, role,
                front, length, width, worldPos, minWorldX, maxWorldX,
                minWorldY, maxWorldY, pixels, tail.sourceCanvasId(),
                tail.surface().orElseGet(() -> inferLegacySurface(worldPos, pixels)),
                tail.manaElements());
    }

    public PositionedGlyph(
            UUID glyphUuid,
            int glyphId,
            ResourceLocation symbolId,
            float confidence,
            SymbolRole role,
            Vec3 front,
            double length,
            double width,
            BlockPos worldPos,
            double minWorldX, double maxWorldX,
            double minWorldY, double maxWorldY,
            Set<PixelPos> pixels,
            Optional<UUID> sourceCanvasId,
            SurfaceFrame surface
    ) {
        this(glyphUuid, glyphId, symbolId, confidence, role, front, length, width,
                worldPos, minWorldX, maxWorldX, minWorldY, maxWorldY, pixels,
                sourceCanvasId, surface, ManaElements.EMPTY);
    }

    public PositionedGlyph(
            UUID glyphUuid,
            int glyphId,
            ResourceLocation symbolId,
            float confidence,
            SymbolRole role,
            Vec3 front,
            double length,
            double width,
            BlockPos worldPos,
            double minWorldX, double maxWorldX,
            double minWorldY, double maxWorldY,
            Set<PixelPos> pixels,
            ManaElements manaElements
    ) {
        this(glyphUuid, glyphId, symbolId, confidence, role, front, length, width,
                worldPos, minWorldX, maxWorldX, minWorldY, maxWorldY, pixels,
                Optional.empty(), inferLegacySurface(worldPos, pixels), manaElements);
    }

    public PositionedGlyph(
            UUID glyphUuid,
            int glyphId,
            ResourceLocation symbolId,
            float confidence,
            SymbolRole role,
            Vec3 front,
            double length,
            double width,
            BlockPos worldPos,
            double minWorldX, double maxWorldX,
            double minWorldY, double maxWorldY,
            Set<PixelPos> pixels
    ) {
        this(glyphUuid, glyphId, symbolId, confidence, role, front, length, width,
                worldPos, minWorldX, maxWorldX, minWorldY, maxWorldY, pixels,
                Optional.empty(), inferLegacySurface(worldPos, pixels));
    }

    public PositionedGlyph(
            UUID glyphUuid, int glyphId, ResourceLocation symbolId, float confidence,
            SymbolRole role, Vec3 front, double length, double width, BlockPos worldPos,
            double minWorldX, double maxWorldX, double minWorldY, double maxWorldY,
            Set<PixelPos> pixels, Optional<UUID> sourceCanvasId
    ) {
        this(glyphUuid, glyphId, symbolId, confidence, role, front, length, width,
                worldPos, minWorldX, maxWorldX, minWorldY, maxWorldY, pixels,
                sourceCanvasId, inferLegacySurface(worldPos, pixels));
    }

    public PositionedGlyph withGlyphId(int newGlyphId) {
        return new PositionedGlyph(glyphUuid, newGlyphId, symbolId, confidence, role,
                front, length, width, worldPos,
                minWorldX, maxWorldX, minWorldY, maxWorldY, pixels, sourceCanvasId,
                surface, manaElements);
    }

    public PositionedGlyph withManaElements(ManaElements elements) {
        return new PositionedGlyph(glyphUuid, glyphId, symbolId, confidence, role,
                front, length, width, worldPos,
                minWorldX, maxWorldX, minWorldY, maxWorldY, pixels, sourceCanvasId,
                surface, elements);
    }

    public SurfaceFrame.SurfaceBounds bounds() {
        return new SurfaceFrame.SurfaceBounds(
                minWorldX, maxWorldX, minWorldY, maxWorldY);
    }

    public SurfaceFrame.SurfaceBounds boundsOn(SurfaceFrame target) {
        return surface.transformBounds(bounds(), target);
    }

    public Vec3 center() {
        return surface.world(
                (minWorldX + maxWorldX) * 0.5,
                (minWorldY + maxWorldY) * 0.5);
    }

    public Optional<net.minecraft.core.Direction> legacyDirection() {
        return surface.axisAlignedDirection();
    }

    public Optional<SurfaceFrame.LegacyBlockFace> legacyBlockFaceAt(Vec3 worldPosition) {
        return surface.legacyBlockFaceAt(worldPosition);
    }

    private static SurfaceFrame inferLegacySurface(BlockPos worldPos, Set<PixelPos> pixels) {
        PixelPos sample = pixels == null ? null : pixels.stream().findFirst().orElse(null);
        return sample != null
                ? SurfaceFrame.fromBlockFace(sample.pos(), sample.face())
                : SurfaceFrame.fromBlockFace(worldPos, net.minecraft.core.Direction.UP);
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
