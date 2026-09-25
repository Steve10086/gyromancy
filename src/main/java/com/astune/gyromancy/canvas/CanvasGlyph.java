package com.astune.gyromancy.canvas;

import com.astune.gyromancy.api.element.ManaElements;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.UUID;

/**
 * A recognized glyph stored in canvas-local coordinates.
 *
 * <p>Coordinates are normalized to the canvas, so changing the raster
 * resolution does not change the glyph's physical size or position.
 */
public record CanvasGlyph(
        UUID glyphUuid,
        ResourceLocation symbolId,
        float confidence,
        SymbolRole role,
        double frontX,
        double frontY,
        double length,
        double width,
        double minX,
        double maxX,
        double minY,
        double maxY,
        int[] cells,
        ManaElements manaElements
) {
    private static final Codec<UUID> UUID_CODEC =
            Codec.STRING.xmap(UUID::fromString, UUID::toString);
    private static final Codec<int[]> INT_ARRAY_CODEC = Codec.INT_STREAM.xmap(
            stream -> stream.toArray(), Arrays::stream);

    public static final Codec<CanvasGlyph> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    UUID_CODEC.fieldOf("uuid").forGetter(CanvasGlyph::glyphUuid),
                    ResourceLocation.CODEC.fieldOf("symbol").forGetter(CanvasGlyph::symbolId),
                    Codec.FLOAT.fieldOf("confidence").forGetter(CanvasGlyph::confidence),
                    SymbolRole.CODEC.fieldOf("role").forGetter(CanvasGlyph::role),
                    Codec.DOUBLE.fieldOf("front_x").forGetter(CanvasGlyph::frontX),
                    Codec.DOUBLE.fieldOf("front_y").forGetter(CanvasGlyph::frontY),
                    Codec.DOUBLE.fieldOf("length").forGetter(CanvasGlyph::length),
                    Codec.DOUBLE.fieldOf("width").forGetter(CanvasGlyph::width),
                    Codec.DOUBLE.fieldOf("min_x").forGetter(CanvasGlyph::minX),
                    Codec.DOUBLE.fieldOf("max_x").forGetter(CanvasGlyph::maxX),
                    Codec.DOUBLE.fieldOf("min_y").forGetter(CanvasGlyph::minY),
                    Codec.DOUBLE.fieldOf("max_y").forGetter(CanvasGlyph::maxY),
                    INT_ARRAY_CODEC.fieldOf("cells").forGetter(CanvasGlyph::cells),
                    ManaElements.CODEC.optionalFieldOf("mana_elements", ManaElements.EMPTY)
                            .forGetter(CanvasGlyph::manaElements))
                    .apply(instance, CanvasGlyph::new));

    public CanvasGlyph(UUID glyphUuid, ResourceLocation symbolId, float confidence,
                       SymbolRole role, double frontX, double frontY, double length,
                       double width, double minX, double maxX, double minY, double maxY,
                       int[] cells) {
        this(glyphUuid, symbolId, confidence, role, frontX, frontY, length, width,
                minX, maxX, minY, maxY, cells, ManaElements.EMPTY);
    }

    public CanvasGlyph {
        cells = cells.clone();
        manaElements = manaElements == null ? ManaElements.EMPTY : manaElements;
    }

    @Override
    public int[] cells() {
        return cells.clone();
    }

    int[] rawCells() {
        return cells;
    }

    /** Returns an equivalent glyph carrying a different identity. */
    public CanvasGlyph withGlyphUuid(UUID uuid) {
        return new CanvasGlyph(uuid, symbolId, confidence, role, frontX, frontY,
                length, width, minX, maxX, minY, maxY, cells, manaElements);
    }

    /** Returns an equivalent glyph carrying solved mana elements. */
    public CanvasGlyph withManaElements(ManaElements elements) {
        return new CanvasGlyph(glyphUuid, symbolId, confidence, role, frontX, frontY,
                length, width, minX, maxX, minY, maxY, cells, elements);
    }

    public CanvasGlyph resampleCells(int oldWidth, int oldHeight, int newWidth, int newHeight) {
        boolean[] owned = new boolean[oldWidth * oldHeight];
        for (int cell : cells) {
            if (cell >= 0 && cell < owned.length) owned[cell] = true;
        }

        int[] scaled = new int[newWidth * newHeight];
        int count = 0;
        for (int y = 0; y < newHeight; y++) {
            int sourceY = Math.min(oldHeight - 1, y * oldHeight / newHeight);
            for (int x = 0; x < newWidth; x++) {
                int sourceX = Math.min(oldWidth - 1, x * oldWidth / newWidth);
                if (owned[sourceY * oldWidth + sourceX]) {
                    scaled[count++] = y * newWidth + x;
                }
            }
        }
        return new CanvasGlyph(glyphUuid, symbolId, confidence, role, frontX, frontY,
                length, width, minX, maxX, minY, maxY, Arrays.copyOf(scaled, count),
                manaElements);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof CanvasGlyph other)) return false;
        return Float.compare(confidence, other.confidence) == 0
                && Double.compare(frontX, other.frontX) == 0
                && Double.compare(frontY, other.frontY) == 0
                && Double.compare(length, other.length) == 0
                && Double.compare(width, other.width) == 0
                && Double.compare(minX, other.minX) == 0
                && Double.compare(maxX, other.maxX) == 0
                && Double.compare(minY, other.minY) == 0
                && Double.compare(maxY, other.maxY) == 0
                && glyphUuid.equals(other.glyphUuid)
                && symbolId.equals(other.symbolId)
                && role == other.role
                && manaElements.equals(other.manaElements)
                && Arrays.equals(cells, other.cells);
    }

    @Override
    public int hashCode() {
        int result = glyphUuid.hashCode();
        result = 31 * result + symbolId.hashCode();
        result = 31 * result + Float.hashCode(confidence);
        result = 31 * result + role.hashCode();
        result = 31 * result + Double.hashCode(frontX);
        result = 31 * result + Double.hashCode(frontY);
        result = 31 * result + Double.hashCode(length);
        result = 31 * result + Double.hashCode(width);
        result = 31 * result + Double.hashCode(minX);
        result = 31 * result + Double.hashCode(maxX);
        result = 31 * result + Double.hashCode(minY);
        result = 31 * result + Double.hashCode(maxY);
        result = 31 * result + manaElements.hashCode();
        return 31 * result + Arrays.hashCode(cells);
    }
}

