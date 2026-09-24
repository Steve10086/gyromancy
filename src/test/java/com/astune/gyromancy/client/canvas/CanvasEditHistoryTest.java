package com.astune.gyromancy.client.canvas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasEditHistoryTest {

    @Test
    void draggedStrokeIsUndoneAndRedoneAsOneAction() {
        int[] colors = new int[4];
        int[] effects = new int[4];
        CanvasEditHistory history = new CanvasEditHistory();

        history.beginAction();
        change(history, colors, effects, 0, 10, 1);
        change(history, colors, effects, 1, 20, 2);
        history.commitAction();

        CanvasEditHistory.RasterState undone = history.undo(1, colors, effects);
        assertNotNull(undone);
        assertEquals(0, undone.colors()[0]);
        assertEquals(0, undone.colors()[1]);
        assertEquals(0, undone.effects()[0]);
        assertEquals(0, undone.effects()[1]);

        CanvasEditHistory.RasterState redone = history.redo(
                undone.scale(), undone.colors(), undone.effects());
        assertNotNull(redone);
        assertEquals(10, redone.colors()[0]);
        assertEquals(20, redone.colors()[1]);
        assertEquals(1, redone.effects()[0]);
        assertEquals(2, redone.effects()[1]);
    }

    @Test
    void repeatedWritesKeepThePixelsOriginalAndFinalValues() {
        int[] colors = {5};
        int[] effects = {1};
        CanvasEditHistory history = new CanvasEditHistory();

        history.beginAction();
        change(history, colors, effects, 0, 10, 2);
        change(history, colors, effects, 0, 15, 3);
        history.commitAction();

        CanvasEditHistory.RasterState undone = history.undo(1, colors, effects);
        assertNotNull(undone);
        assertEquals(5, undone.colors()[0]);
        assertEquals(1, undone.effects()[0]);
        CanvasEditHistory.RasterState redone = history.redo(
                undone.scale(), undone.colors(), undone.effects());
        assertNotNull(redone);
        assertEquals(15, redone.colors()[0]);
        assertEquals(3, redone.effects()[0]);
    }

    @Test
    void newStrokeAfterUndoDiscardsRedoBranch() {
        int[] colors = new int[2];
        int[] effects = new int[2];
        CanvasEditHistory history = new CanvasEditHistory();

        history.beginAction();
        change(history, colors, effects, 0, 10, 1);
        history.commitAction();
        CanvasEditHistory.RasterState undone = history.undo(1, colors, effects);
        assertNotNull(undone);
        assertTrue(history.canRedo());

        history.beginAction();
        change(history, undone.colors(), undone.effects(), 1, 20, 2);
        history.commitAction();

        assertFalse(history.canRedo());
        assertNull(history.redo(
                undone.scale(), undone.colors(), undone.effects()));
    }

    @Test
    void returningPixelToItsOriginalValueCreatesNoHistoryEntry() {
        int[] colors = {5};
        int[] effects = {1};
        CanvasEditHistory history = new CanvasEditHistory();

        history.beginAction();
        change(history, colors, effects, 0, 10, 2);
        change(history, colors, effects, 0, 5, 1);
        history.commitAction();

        assertFalse(history.canUndo());
    }

    @Test
    void resolutionChangeCanBeUndoneAndRedoneWithDifferentMatrixSizes() {
        CanvasEditHistory history = new CanvasEditHistory();
        int[] lowColors = {1, 2, 3, 4};
        int[] lowEffects = {5, 6, 7, 8};
        int[] highColors = new int[16];
        int[] highEffects = new int[16];
        highColors[15] = 40;
        highEffects[15] = 80;

        history.recordResolutionChange(
                1, lowColors, lowEffects,
                2, highColors, highEffects);

        CanvasEditHistory.RasterState undone =
                history.undo(2, highColors, highEffects);
        assertNotNull(undone);
        assertEquals(1, undone.scale());
        assertEquals(4, undone.colors().length);
        assertEquals(4, undone.colors()[3]);
        assertEquals(8, undone.effects()[3]);

        CanvasEditHistory.RasterState redone = history.redo(
                undone.scale(), undone.colors(), undone.effects());
        assertNotNull(redone);
        assertEquals(2, redone.scale());
        assertEquals(16, redone.colors().length);
        assertEquals(40, redone.colors()[15]);
        assertEquals(80, redone.effects()[15]);
    }

    @Test
    void strokesOnBothSidesOfResolutionChangeKeepTheirUndoOrder() {
        CanvasEditHistory history = new CanvasEditHistory();
        int[] lowColors = new int[4];
        int[] lowEffects = new int[4];

        history.beginAction();
        change(history, lowColors, lowEffects, 1, 10, 1);
        history.commitAction();

        int[] highColors = new int[16];
        int[] highEffects = new int[16];
        highColors[5] = 10;
        highEffects[5] = 1;
        history.recordResolutionChange(
                1, lowColors, lowEffects,
                2, highColors, highEffects);

        history.beginAction();
        change(history, highColors, highEffects, 15, 20, 2);
        history.commitAction();

        CanvasEditHistory.RasterState afterHighStrokeUndo =
                history.undo(2, highColors, highEffects);
        assertNotNull(afterHighStrokeUndo);
        assertEquals(0, afterHighStrokeUndo.colors()[15]);

        CanvasEditHistory.RasterState afterResolutionUndo = history.undo(
                afterHighStrokeUndo.scale(),
                afterHighStrokeUndo.colors(),
                afterHighStrokeUndo.effects());
        assertNotNull(afterResolutionUndo);
        assertEquals(1, afterResolutionUndo.scale());
        assertEquals(10, afterResolutionUndo.colors()[1]);

        CanvasEditHistory.RasterState afterLowStrokeUndo = history.undo(
                afterResolutionUndo.scale(),
                afterResolutionUndo.colors(),
                afterResolutionUndo.effects());
        assertNotNull(afterLowStrokeUndo);
        assertEquals(0, afterLowStrokeUndo.colors()[1]);

        CanvasEditHistory.RasterState afterLowStrokeRedo = history.redo(
                afterLowStrokeUndo.scale(),
                afterLowStrokeUndo.colors(),
                afterLowStrokeUndo.effects());
        assertNotNull(afterLowStrokeRedo);
        assertEquals(10, afterLowStrokeRedo.colors()[1]);

        CanvasEditHistory.RasterState afterResolutionRedo = history.redo(
                afterLowStrokeRedo.scale(),
                afterLowStrokeRedo.colors(),
                afterLowStrokeRedo.effects());
        assertNotNull(afterResolutionRedo);
        assertEquals(2, afterResolutionRedo.scale());
        assertEquals(16, afterResolutionRedo.colors().length);

        CanvasEditHistory.RasterState afterHighStrokeRedo = history.redo(
                afterResolutionRedo.scale(),
                afterResolutionRedo.colors(),
                afterResolutionRedo.effects());
        assertNotNull(afterHighStrokeRedo);
        assertEquals(20, afterHighStrokeRedo.colors()[15]);
        assertEquals(2, afterHighStrokeRedo.effects()[15]);
    }

    @Test
    void transformIsUndoneAndRedoneAtTheCurrentScale() {
        CanvasEditHistory history = new CanvasEditHistory();
        int[] colors = {1, 2, 3, 4};
        int[] effects = {5, 6, 7, 8};
        int[] rotatedColors = {3, 1, 4, 2};
        int[] rotatedEffects = {7, 5, 8, 6};

        history.recordTransform(1, colors, effects, rotatedColors, rotatedEffects);
        assertTrue(history.canUndo());

        CanvasEditHistory.RasterState undone =
                history.undo(1, rotatedColors, rotatedEffects);
        assertNotNull(undone);
        assertEquals(1, undone.scale());
        assertArrayEquals(colors, undone.colors());
        assertArrayEquals(effects, undone.effects());

        CanvasEditHistory.RasterState redone = history.redo(
                undone.scale(), undone.colors(), undone.effects());
        assertNotNull(redone);
        assertArrayEquals(rotatedColors, redone.colors());
        assertArrayEquals(rotatedEffects, redone.effects());
    }

    @Test
    void transformThatChangesNothingCreatesNoHistoryEntry() {
        CanvasEditHistory history = new CanvasEditHistory();
        int[] colors = {1, 2, 3, 4};
        int[] effects = {5, 6, 7, 8};

        history.recordTransform(1, colors, effects, colors.clone(), effects.clone());

        assertFalse(history.canUndo());
    }

    private static void change(CanvasEditHistory history,
                               int[] colors,
                               int[] effects,
                               int index,
                               int color,
                               int effect) {
        history.recordChange(index, colors[index], effects[index], color, effect);
        colors[index] = color;
        effects[index] = effect;
    }
}
