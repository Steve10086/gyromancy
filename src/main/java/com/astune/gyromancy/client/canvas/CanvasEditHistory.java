package com.astune.gyromancy.client.canvas;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-only edit history owned by one canvas editor screen.
 *
 * <p>Strokes store only the pixels they changed, while resolution changes
 * store their before/after raster states so the history can cross matrix
 * dimensions. Repeated writes to one pixel in a stroke are folded together.
 */
final class CanvasEditHistory {
    private final Deque<EditAction> undo = new ArrayDeque<>();
    private final Deque<EditAction> redo = new ArrayDeque<>();
    private Map<Integer, MutablePixelChange> activeChanges;

    void beginAction() {
        commitAction();
        activeChanges = new LinkedHashMap<>();
    }

    void recordChange(int index,
                      int beforeColor,
                      int beforeEffect,
                      int afterColor,
                      int afterEffect) {
        if (activeChanges == null) beginAction();

        MutablePixelChange change = activeChanges.get(index);
        if (change == null) {
            if (beforeColor != afterColor || beforeEffect != afterEffect) {
                activeChanges.put(index, new MutablePixelChange(
                        index, beforeColor, beforeEffect, afterColor, afterEffect));
            }
            return;
        }

        change.afterColor = afterColor;
        change.afterEffect = afterEffect;
        if (change.beforeColor == afterColor && change.beforeEffect == afterEffect) {
            activeChanges.remove(index);
        }
    }

    boolean commitAction() {
        if (activeChanges == null) return false;
        if (activeChanges.isEmpty()) {
            activeChanges = null;
            return false;
        }

        List<PixelChange> changes = new ArrayList<>(activeChanges.size());
        for (MutablePixelChange change : activeChanges.values()) {
            changes.add(change.freeze());
        }
        undo.push(new PixelEdit(List.copyOf(changes)));
        redo.clear();
        activeChanges = null;
        return true;
    }

    void recordResolutionChange(int beforeScale,
                                int[] beforeColors,
                                int[] beforeEffects,
                                int afterScale,
                                int[] afterColors,
                                int[] afterEffects) {
        commitAction();
        if (beforeScale == afterScale
                && Arrays.equals(beforeColors, afterColors)
                && Arrays.equals(beforeEffects, afterEffects)) {
            return;
        }
        undo.push(new ResolutionEdit(
                RasterState.snapshot(beforeScale, beforeColors, beforeEffects),
                RasterState.snapshot(afterScale, afterColors, afterEffects)));
        redo.clear();
    }

    RasterState undo(int scale, int[] colors, int[] effects) {
        commitAction();
        if (undo.isEmpty()) return null;
        EditAction edit = undo.pop();
        RasterState result = edit.applyBefore(new RasterState(scale, colors, effects));
        redo.push(edit);
        return result;
    }

    RasterState redo(int scale, int[] colors, int[] effects) {
        commitAction();
        if (redo.isEmpty()) return null;
        EditAction edit = redo.pop();
        RasterState result = edit.applyAfter(new RasterState(scale, colors, effects));
        undo.push(edit);
        return result;
    }

    boolean canUndo() {
        return (activeChanges != null && !activeChanges.isEmpty()) || !undo.isEmpty();
    }

    boolean canRedo() {
        return !redo.isEmpty();
    }

    void clear() {
        undo.clear();
        redo.clear();
        activeChanges = null;
    }

    private record PixelChange(
            int index,
            int beforeColor,
            int beforeEffect,
            int afterColor,
            int afterEffect
    ) {}

    record RasterState(int scale, int[] colors, int[] effects) {
        private static RasterState snapshot(int scale, int[] colors, int[] effects) {
            return new RasterState(scale, colors.clone(), effects.clone());
        }

        private RasterState copy() {
            return snapshot(scale, colors, effects);
        }
    }

    private interface EditAction {
        RasterState applyBefore(RasterState current);

        RasterState applyAfter(RasterState current);
    }

    private record PixelEdit(List<PixelChange> changes) implements EditAction {
        @Override
        public RasterState applyBefore(RasterState current) {
            for (PixelChange change : changes) {
                current.colors[change.index] = change.beforeColor;
                current.effects[change.index] = change.beforeEffect;
            }
            return current;
        }

        @Override
        public RasterState applyAfter(RasterState current) {
            for (PixelChange change : changes) {
                current.colors[change.index] = change.afterColor;
                current.effects[change.index] = change.afterEffect;
            }
            return current;
        }
    }

    private record ResolutionEdit(RasterState before, RasterState after) implements EditAction {
        @Override
        public RasterState applyBefore(RasterState current) {
            return before.copy();
        }

        @Override
        public RasterState applyAfter(RasterState current) {
            return after.copy();
        }
    }

    private static final class MutablePixelChange {
        private final int index;
        private final int beforeColor;
        private final int beforeEffect;
        private int afterColor;
        private int afterEffect;

        private MutablePixelChange(int index,
                                   int beforeColor,
                                   int beforeEffect,
                                   int afterColor,
                                   int afterEffect) {
            this.index = index;
            this.beforeColor = beforeColor;
            this.beforeEffect = beforeEffect;
            this.afterColor = afterColor;
            this.afterEffect = afterEffect;
        }

        private PixelChange freeze() {
            return new PixelChange(index, beforeColor, beforeEffect, afterColor, afterEffect);
        }
    }
}
