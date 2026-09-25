package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.element.ManaElements;
import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.element.ManaIdTable;
import com.astune.painter.api.CanvasFace;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Solves the mana elements carried by a single recognized symbol. */
public final class SymbolMana {
    private SymbolMana() {}

    /** Block-canvas symbols use the canvas face effect value at unit pixel size. */
    public static ManaElements solveBlockGlyph(Set<PixelPos> pixels, ServerLevel level) {
        return solve(countBlockPixels(pixels, level), 1.0);
    }

    /** Canvas symbols read ids from the document raster and scale by pixel area. */
    public static ManaElements solveCanvasGlyph(int[] effects, int[] cells, double pixelArea) {
        return solve(countCanvasCells(effects, cells), pixelArea);
    }

    static Map<Integer, Integer> countBlockPixels(Set<PixelPos> pixels, ServerLevel level) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (PixelPos pixel : pixels) {
            CanvasFace face = FloodFillExtractor.getFaceAt(level, pixel.pos(), pixel.face());
            if (face == null) continue;
            int id = face.getEffectValue(ManaPixelDetector.MANA_EFFECT_KEY, pixel.x(), pixel.y());
            counts.merge(id, 1, Integer::sum);
        }
        return counts;
    }

    static Map<Integer, Integer> countCanvasCells(int[] effects, int[] cells) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int cell : cells) {
            if (cell < 0 || cell >= effects.length) continue;
            counts.merge(effects[cell], 1, Integer::sum);
        }
        return counts;
    }

    static ManaElements solve(Map<Integer, Integer> idCounts, double pixelArea) {
        double[] values = new double[ElementType.COUNT];
        for (Map.Entry<Integer, Integer> entry : idCounts.entrySet()) {
            if (entry.getValue() == null || entry.getValue() == 0) continue;
            int[] row = ManaIdTable.rowFor(entry.getKey());
            for (int slot = 0; slot < values.length; slot++) {
                values[slot] += row[slot] * entry.getValue();
            }
        }
        for (int slot = 0; slot < values.length; slot++) {
            values[slot] *= pixelArea;
        }
        return new ManaElements(values);
    }

    /**
     * Returns the mana id carried by most of the given cell sets, used by
     * projections to inherit their source array's dominant signature.
     */
    public static int dominantId(int[] effects, Iterable<int[]> cellSets) {
        Map<Integer, Integer> counts = countCanvasCells(effects, flatten(cellSets));
        int bestId = ManaIdTable.FALLBACK_ID;
        int bestCount = 0;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                bestId = entry.getKey();
            }
        }
        return bestId;
    }

    private static int[] flatten(Iterable<int[]> cellSets) {
        int[] merged = new int[0];
        for (int[] cells : cellSets) {
            int[] next = new int[merged.length + cells.length];
            System.arraycopy(merged, 0, next, 0, merged.length);
            System.arraycopy(cells, 0, next, merged.length, cells.length);
            merged = next;
        }
        return merged;
    }
}
