package com.astune.gyromancy.canvas;

import java.util.BitSet;

/** Server-authored dirty set for an editor submission. */
public record CanvasEditDiff(int width, int height, BitSet changed, BitSet compileRegion) {

    public CanvasEditDiff {
        changed = (BitSet) changed.clone();
        compileRegion = (BitSet) compileRegion.clone();
    }

    @Override
    public BitSet changed() {
        return (BitSet) changed.clone();
    }

    @Override
    public BitSet compileRegion() {
        return (BitSet) compileRegion.clone();
    }

    public static CanvasEditDiff between(CanvasDocument before, CanvasDocument after) {
        if (before.resolutionWidth() != after.resolutionWidth()
                || before.resolutionHeight() != after.resolutionHeight()) {
            throw new IllegalArgumentException("Diff inputs must have the same resolution");
        }
        int width = before.resolutionWidth();
        int height = before.resolutionHeight();
        BitSet changed = new BitSet(width * height);
        int[] beforeColors = before.rawColors();
        int[] afterColors = after.rawColors();
        int[] beforeEffects = before.rawStrokeEffects();
        int[] afterEffects = after.rawStrokeEffects();
        for (int index = 0; index < beforeColors.length; index++) {
            if (beforeColors[index] != afterColors[index]
                    || beforeEffects[index] != afterEffects[index]) {
                changed.set(index);
            }
        }

        return new CanvasEditDiff(width, height, changed,
                expandRegion(width, height, changed));
    }

    /** Expands changed pixels by one cell using the recognizer's 8-connectivity. */
    public static BitSet expandRegion(int width, int height, BitSet changed) {
        BitSet compileRegion = new BitSet(width * height);
        for (int index = changed.nextSetBit(0);
             index >= 0;
             index = changed.nextSetBit(index + 1)) {
            int x = index % width;
            int y = index / width;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx;
                    int ny = y + dy;
                    if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                        compileRegion.set(ny * width + nx);
                    }
                }
            }
        }
        return compileRegion;
    }

    public boolean isEmpty() {
        return changed.isEmpty();
    }

    public boolean touches(CanvasGlyph glyph) {
        for (int cell : glyph.rawCells()) {
            if (compileRegion.get(cell)) return true;
        }
        return false;
    }
}
