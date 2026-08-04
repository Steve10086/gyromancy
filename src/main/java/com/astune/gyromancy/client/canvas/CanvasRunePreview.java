package com.astune.gyromancy.client.canvas;

import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.SymbolMatch;
import com.astune.gyromancy.api.symbol.SymbolTemplate;
import com.astune.gyromancy.registry.GyromancyRegistries;
import com.astune.gyromancy.symbol.FloodFillExtractor.ExtractedGlyph;
import com.astune.gyromancy.symbol.SymbolRecognizer;
import com.astune.gyromancy.util.CanvasScanUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Client-only glyph recognition result used by the canvas editor preview. */
final class CanvasRunePreview {
    private static final CanvasRunePreview EMPTY =
            new CanvasRunePreview(0, 0, List.of());

    private final int width;
    private final int height;
    private final List<RuneMatch> runes;
    private final int[] runeByPixel;

    private CanvasRunePreview(int width, int height, List<RuneMatch> runes) {
        this.width = width;
        this.height = height;
        this.runes = List.copyOf(runes);
        this.runeByPixel = new int[Math.max(0, width * height)];
        Arrays.fill(runeByPixel, -1);
        for (int runeIndex = 0; runeIndex < runes.size(); runeIndex++) {
            for (int cell : runes.get(runeIndex).cells()) {
                if (cell >= 0 && cell < runeByPixel.length) {
                    runeByPixel[cell] = runeIndex;
                }
            }
        }
    }

    static CanvasRunePreview empty() {
        return EMPTY;
    }

    static CanvasRunePreview compile(int width, int height, int[] effects) {
        if (width <= 0 || height <= 0 || effects.length != width * height) {
            return EMPTY;
        }

        int[][] matrix = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                matrix[y][x] = effects[y * width + x] > 0 ? 1 : 0;
            }
        }

        List<RuneMatch> runes = new ArrayList<>();
        for (CanvasScanUtils.ConnectedComponent component
                : CanvasScanUtils.extractComponents(matrix, 1)) {
            int[] cells = componentCells(component, width);
            ExtractedGlyph glyph = extractedGlyph(component, cells, width);
            List<SymbolMatch> matches = SymbolRecognizer.recognize(
                    glyph,
                    GyromancyRegistries.SYMBOL,
                    SymbolRecognizer.RecognizerConfig.CLIENT_PREVIEW);
            if (matches.isEmpty()) continue;

            SymbolMatch best = matches.getFirst();
            SymbolTemplate template = GyromancyRegistries.SYMBOL.get(best.symbolId());
            runes.add(new RuneMatch(
                    best.symbolId(),
                    best.confidence(),
                    template == null ? 0xFFFFFFFF : template.glyphColor(),
                    cells));
        }
        return runes.isEmpty() ? EMPTY : new CanvasRunePreview(width, height, runes);
    }

    static CanvasRunePreview of(int width, int height, List<RuneMatch> runes) {
        return new CanvasRunePreview(width, height, runes);
    }

    int[] colorize(int[] colors) {
        if (colors.length != runeByPixel.length) return colors.clone();
        int[] result = colors.clone();
        for (int index = 0; index < runeByPixel.length; index++) {
            int runeIndex = runeByPixel[index];
            if (runeIndex >= 0) result[index] = runes.get(runeIndex).color();
        }
        return result;
    }

    Optional<RuneMatch> runeAt(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return Optional.empty();
        int runeIndex = runeByPixel[y * width + x];
        return runeIndex < 0 ? Optional.empty() : Optional.of(runes.get(runeIndex));
    }

    private static int[] componentCells(
            CanvasScanUtils.ConnectedComponent component, int canvasWidth) {
        int[][] pixels = component.pixels();
        int[] cells = new int[component.area()];
        int count = 0;
        for (int y = 0; y < pixels.length; y++) {
            for (int x = 0; x < pixels[y].length; x++) {
                if (pixels[y][x] == 0) continue;
                cells[count++] = (component.minY() + y) * canvasWidth
                        + component.minX() + x;
            }
        }
        return count == cells.length ? cells : Arrays.copyOf(cells, count);
    }

    private static ExtractedGlyph extractedGlyph(
            CanvasScanUtils.ConnectedComponent component,
            int[] cells,
            int width) {
        Set<PixelPos> pixels = new LinkedHashSet<>();
        double[] xs = new double[cells.length];
        double[] ys = new double[cells.length];
        for (int i = 0; i < cells.length; i++) {
            int x = cells[i] % width;
            int y = cells[i] / width;
            pixels.add(new PixelPos(BlockPos.ZERO, Direction.NORTH, x, y, 0));
            xs[i] = x;
            ys[i] = y;
        }
        return new ExtractedGlyph(
                Set.copyOf(pixels), xs, ys,
                component.minX(), component.maxX(),
                component.minY(), component.maxY(),
                1);
    }

    record RuneMatch(
            ResourceLocation symbolId,
            float confidence,
            int color,
            int[] cells) {
        RuneMatch {
            cells = cells.clone();
        }

        @Override
        public int[] cells() {
            return cells.clone();
        }
    }
}
