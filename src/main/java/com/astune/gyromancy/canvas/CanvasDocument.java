package com.astune.gyromancy.canvas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Complete portable canvas state shared by the placed entity and its item.
 *
 * <p>The raster is stored as two logical 2-D matrices in row-major form:
 * ARGB colors and the current stroke effect. A positive effect value is a
 * mana stroke and is intentionally compatible with the existing recognizer.
 */
public record CanvasDocument(
        int physicalWidth,
        int physicalHeight,
        int resolutionScale,
        int[] colors,
        int[] strokeEffects,
        List<CanvasGlyph> glyphs,
        List<CanvasArrayRecord> arrays
) {
    public static final int PIXELS_PER_BLOCK = 16;
    public static final int MAX_RESOLUTION = 256;
    public static final int MAX_PHYSICAL_SIZE = 16;
    public static final int MAX_RESOLUTION_SCALE = MAX_RESOLUTION / PIXELS_PER_BLOCK;

    private static final Codec<int[]> INT_ARRAY_CODEC = Codec.INT_STREAM.xmap(
            stream -> stream.toArray(), Arrays::stream);

    public static final Codec<CanvasDocument> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("physical_width").forGetter(CanvasDocument::physicalWidth),
                    Codec.INT.fieldOf("physical_height").forGetter(CanvasDocument::physicalHeight),
                    Codec.INT.fieldOf("resolution_scale").forGetter(CanvasDocument::resolutionScale),
                    INT_ARRAY_CODEC.fieldOf("colors").forGetter(CanvasDocument::colors),
                    INT_ARRAY_CODEC.fieldOf("stroke_effects").forGetter(CanvasDocument::strokeEffects),
                    CanvasGlyph.CODEC.listOf().optionalFieldOf("glyphs", List.of())
                            .forGetter(CanvasDocument::glyphs),
                    CanvasArrayRecord.CODEC.listOf().optionalFieldOf("arrays", List.of())
                            .forGetter(CanvasDocument::arrays))
                    .apply(instance, CanvasDocument::new));

    public CanvasDocument {
        if (physicalWidth < 1 || physicalWidth > MAX_PHYSICAL_SIZE
                || physicalHeight < 1 || physicalHeight > MAX_PHYSICAL_SIZE) {
            throw new IllegalArgumentException("Canvas physical size must be between 1 and "
                    + MAX_PHYSICAL_SIZE + " blocks");
        }
        if (resolutionScale < 1 || resolutionScale > MAX_RESOLUTION_SCALE) {
            throw new IllegalArgumentException("Canvas resolution scale is out of range");
        }
        int expected = PIXELS_PER_BLOCK * resolutionScale
                * PIXELS_PER_BLOCK * resolutionScale;
        if (colors.length != expected || strokeEffects.length != expected) {
            throw new IllegalArgumentException("Canvas matrix dimensions do not match its resolution scale");
        }
        colors = colors.clone();
        strokeEffects = strokeEffects.clone();
        glyphs = List.copyOf(glyphs);
        arrays = List.copyOf(arrays);
    }

    public static CanvasDocument blank(int physicalWidth, int physicalHeight) {
        int length = PIXELS_PER_BLOCK * PIXELS_PER_BLOCK;
        return new CanvasDocument(physicalWidth, physicalHeight, 1,
                new int[length], new int[length], List.of(), List.of());
    }

    public static int maxScale() {
        return MAX_RESOLUTION_SCALE;
    }

    public int resolutionWidth() {
        return PIXELS_PER_BLOCK * resolutionScale;
    }

    public int resolutionHeight() {
        return PIXELS_PER_BLOCK * resolutionScale;
    }

    /**
     * World-area coefficient of one raster pixel for the symbol mana solve.
     * Canvas pixels are square: a block canvas has area 1, larger or
     * higher-resolution canvases scale by {@code (size / resolutionScale)} per
     * axis.
     */
    public double manaPixelArea() {
        return (physicalWidth / (double) resolutionScale)
                * (physicalHeight / (double) resolutionScale);
    }

    @Override
    public int[] colors() {
        return colors.clone();
    }

    /**
     * Returns the color raster for coordinate systems whose vertical origin
     * is at the bottom. The document itself remains top-origin so editor and
     * recognizer coordinates are unchanged.
     */
    public int[] colorsBottomToTop() {
        int width = resolutionWidth();
        int height = resolutionHeight();
        int[] result = new int[colors.length];
        for (int y = 0; y < height; y++) {
            System.arraycopy(
                    colors,
                    y * width,
                    result,
                    (height - 1 - y) * width,
                    width);
        }
        return result;
    }

    int[] rawColors() {
        return colors;
    }

    @Override
    public int[] strokeEffects() {
        return strokeEffects.clone();
    }

    int[] rawStrokeEffects() {
        return strokeEffects;
    }

    public int colorAt(int x, int y) {
        return colors[y * resolutionWidth() + x];
    }

    public int strokeEffectAt(int x, int y) {
        return strokeEffects[y * resolutionWidth() + x];
    }

    /** Returns whether this document has no visible raster content. */
    public boolean isEmpty() {
        for (int color : colors) {
            if (color != 0) return false;
        }
        for (int effect : strokeEffects) {
            if (effect != 0) return false;
        }
        return true;
    }

    public CanvasDocument withRaster(int[] newColors, int[] newStrokeEffects) {
        return new CanvasDocument(physicalWidth, physicalHeight, resolutionScale,
                newColors, newStrokeEffects, glyphs, arrays);
    }

    public CanvasDocument withCompileCache(List<CanvasGlyph> newGlyphs,
                                           List<CanvasArrayRecord> newArrays) {
        return new CanvasDocument(physicalWidth, physicalHeight, resolutionScale,
                colors, strokeEffects, newGlyphs, newArrays);
    }

    /**
     * Increases only the entity's placed size. Raster data and canvas-local
     * compile caches remain unchanged, so the same drawing covers a larger
     * area when this document is placed in the world.
     */
    public CanvasDocument increasePhysicalSize() {
        if (!canIncreasePhysicalSize()) {
            throw new IllegalStateException("Canvas is already at its maximum physical size");
        }
        return new CanvasDocument(physicalWidth + 1, physicalHeight + 1, resolutionScale,
                colors, strokeEffects, glyphs, arrays);
    }

    public boolean canIncreasePhysicalSize() {
        return physicalWidth < MAX_PHYSICAL_SIZE && physicalHeight < MAX_PHYSICAL_SIZE;
    }

    /**
     * Creates an independent one-block canvas carrying this document's exact
     * raster and compile cache. Glyph identities are regenerated so placing
     * the copy beside the source cannot make the two canvases share runtime
     * registrations.
     */
    public CanvasDocument duplicateAsSingleBlock() {
        CanvasDocument fresh = withFreshGlyphIdentities();
        return new CanvasDocument(1, 1, resolutionScale,
                fresh.colors, fresh.strokeEffects, fresh.glyphs, fresh.arrays);
    }

    /**
     * Returns a copy whose glyphs carry newly minted identities and whose array
     * records reference them. Placed canvases always use this form so the same
     * item can never share runtime glyph state with another placed canvas.
     */
    public CanvasDocument withFreshGlyphIdentities() {
        if (glyphs.isEmpty()) return this;
        Map<UUID, UUID> replacements = new HashMap<>();
        List<CanvasGlyph> fresh = new ArrayList<>(glyphs.size());
        for (CanvasGlyph glyph : glyphs) {
            UUID next = UUID.randomUUID();
            replacements.put(glyph.glyphUuid(), next);
            fresh.add(glyph.withGlyphUuid(next));
        }
        return new CanvasDocument(physicalWidth, physicalHeight, resolutionScale,
                colors, strokeEffects, fresh, remapArrays(replacements));
    }

    /**
     * Returns a copy that keeps the recognized structure but replaces every
     * runtime identity with a deterministic positional placeholder. Items
     * store this form; placement mints the real identities again.
     */
    public CanvasDocument withoutRuntimeIdentities() {
        if (glyphs.isEmpty()) return this;
        Map<UUID, UUID> replacements = new HashMap<>();
        List<CanvasGlyph> placeholders = new ArrayList<>(glyphs.size());
        for (int index = 0; index < glyphs.size(); index++) {
            CanvasGlyph glyph = glyphs.get(index);
            UUID placeholder = new UUID(0L, index + 1L);
            replacements.put(glyph.glyphUuid(), placeholder);
            placeholders.add(glyph.withGlyphUuid(placeholder));
        }
        return new CanvasDocument(physicalWidth, physicalHeight, resolutionScale,
                colors, strokeEffects, placeholders, remapArrays(replacements));
    }

    private List<CanvasArrayRecord> remapArrays(Map<UUID, UUID> replacements) {
        return arrays.stream()
                .map(array -> {
                    UUID root = replacements.getOrDefault(array.rootGlyph(), array.rootGlyph());
                    List<UUID> bound = array.boundGlyphs().stream()
                            .map(id -> replacements.getOrDefault(id, id))
                            .toList();
                    return new CanvasArrayRecord(
                            root, bound, CanvasArrayRecord.fingerprint(root, bound), array.color());
                })
                .toList();
    }

    public CanvasDocument resample(int newScale) {
        if (newScale == resolutionScale) return this;
        if (newScale < 1 || newScale > MAX_RESOLUTION_SCALE) {
            throw new IllegalArgumentException("Canvas resolution exceeds 256x256");
        }

        int oldWidth = resolutionWidth();
        int oldHeight = resolutionHeight();
        int newWidth = PIXELS_PER_BLOCK * newScale;
        int newHeight = PIXELS_PER_BLOCK * newScale;
        int[] newColors = resampleMatrix(colors, oldWidth, oldHeight, newWidth, newHeight);
        int[] newEffects = resampleMatrix(strokeEffects, oldWidth, oldHeight, newWidth, newHeight);
        List<CanvasGlyph> newGlyphs = glyphs.stream()
                .map(glyph -> glyph.resampleCells(oldWidth, oldHeight, newWidth, newHeight))
                .toList();
        return new CanvasDocument(physicalWidth, physicalHeight, newScale,
                newColors, newEffects, newGlyphs, arrays);
    }

    private static int[] resampleMatrix(int[] source, int oldWidth, int oldHeight,
                                        int newWidth, int newHeight) {
        int[] result = new int[newWidth * newHeight];
        for (int y = 0; y < newHeight; y++) {
            int sourceY = Math.min(oldHeight - 1, y * oldHeight / newHeight);
            for (int x = 0; x < newWidth; x++) {
                int sourceX = Math.min(oldWidth - 1, x * oldWidth / newWidth);
                result[y * newWidth + x] = source[sourceY * oldWidth + sourceX];
            }
        }
        return result;
    }

    public boolean rasterEquals(CanvasDocument other) {
        return physicalWidth == other.physicalWidth
                && physicalHeight == other.physicalHeight
                && resolutionScale == other.resolutionScale
                && Arrays.equals(colors, other.colors)
                && Arrays.equals(strokeEffects, other.strokeEffects);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof CanvasDocument other)) return false;
        return physicalWidth == other.physicalWidth
                && physicalHeight == other.physicalHeight
                && resolutionScale == other.resolutionScale
                && Arrays.equals(colors, other.colors)
                && Arrays.equals(strokeEffects, other.strokeEffects)
                && glyphs.equals(other.glyphs)
                && arrays.equals(other.arrays);
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(physicalWidth);
        result = 31 * result + Integer.hashCode(physicalHeight);
        result = 31 * result + Integer.hashCode(resolutionScale);
        result = 31 * result + Arrays.hashCode(colors);
        result = 31 * result + Arrays.hashCode(strokeEffects);
        result = 31 * result + glyphs.hashCode();
        return 31 * result + arrays.hashCode();
    }
}
