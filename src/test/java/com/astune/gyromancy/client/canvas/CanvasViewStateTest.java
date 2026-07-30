package com.astune.gyromancy.client.canvas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasViewStateTest {
    private static final double EPSILON = 1.0E-9;

    @Test
    void defaultViewUsesFittedCanvasRectangle() {
        CanvasViewState view = new CanvasViewState();
        CanvasViewState.Rect fitted = new CanvasViewState.Rect(10, 20, 100, 50);

        CanvasViewState.DisplayRect display = view.displayRect(fitted);

        assertEquals(10.0, display.x(), EPSILON);
        assertEquals(20.0, display.y(), EPSILON);
        assertEquals(100.0, display.width(), EPSILON);
        assertEquals(50.0, display.height(), EPSILON);
    }

    @Test
    void zoomKeepsPointUnderPointerAnchored() {
        CanvasViewState view = new CanvasViewState();
        CanvasViewState.Rect fitted = new CanvasViewState.Rect(0, 0, 100, 100);
        CanvasViewState.Rect viewport = new CanvasViewState.Rect(-100, -100, 300, 300);
        double pointerX = 25.0;
        double pointerY = 40.0;

        view.zoomAt(1.0, pointerX, pointerY, fitted, viewport);
        CanvasViewState.DisplayRect display = view.displayRect(fitted);

        assertEquals(pointerX, display.x() + display.width() * 0.25, EPSILON);
        assertEquals(pointerY, display.y() + display.height() * 0.40, EPSILON);
    }

    @Test
    void panningCannotMoveCanvasCompletelyOutOfViewport() {
        CanvasViewState view = new CanvasViewState();
        CanvasViewState.Rect fitted = new CanvasViewState.Rect(0, 0, 100, 100);
        CanvasViewState.Rect viewport = new CanvasViewState.Rect(0, 0, 100, 100);

        view.panBy(1000, 1000, fitted, viewport);
        CanvasViewState.DisplayRect bottomRight = view.displayRect(fitted);
        assertTrue(bottomRight.x() <= 76.0);
        assertTrue(bottomRight.y() <= 76.0);

        view.panBy(-2000, -2000, fitted, viewport);
        CanvasViewState.DisplayRect topLeft = view.displayRect(fitted);
        assertTrue(topLeft.right() >= 24.0);
        assertTrue(topLeft.bottom() >= 24.0);
    }

    @Test
    void zoomIsLimitedToSupportedRange() {
        CanvasViewState view = new CanvasViewState();
        CanvasViewState.Rect fitted = new CanvasViewState.Rect(0, 0, 100, 100);
        CanvasViewState.Rect viewport = new CanvasViewState.Rect(0, 0, 100, 100);

        view.zoomAt(100, 50, 50, fitted, viewport);
        assertEquals(CanvasViewState.MAX_ZOOM, view.zoom(), EPSILON);
        view.zoomAt(-200, 50, 50, fitted, viewport);
        assertEquals(CanvasViewState.MIN_ZOOM, view.zoom(), EPSILON);
    }
}
