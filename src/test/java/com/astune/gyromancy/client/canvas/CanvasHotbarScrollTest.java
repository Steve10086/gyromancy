package com.astune.gyromancy.client.canvas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanvasHotbarScrollTest {
    @Test
    void verticalScrollUsesVanillaDirection() {
        CanvasHotbarScroll scroll = new CanvasHotbarScroll();

        assertEquals(1, scroll.add(0.0, 1.0));
        assertEquals(-1, scroll.add(0.0, -1.0));
    }

    @Test
    void horizontalScrollUsesOppositeDirectionLikeVanilla() {
        CanvasHotbarScroll scroll = new CanvasHotbarScroll();

        assertEquals(-1, scroll.add(1.0, 0.0));
        assertEquals(1, scroll.add(-1.0, 0.0));
    }

    @Test
    void fractionalTrackpadScrollAccumulatesToWholeSteps() {
        CanvasHotbarScroll scroll = new CanvasHotbarScroll();

        assertEquals(0, scroll.add(0.0, 0.4));
        assertEquals(0, scroll.add(0.0, 0.4));
        assertEquals(1, scroll.add(0.0, 0.4));
    }

    @Test
    void reversingDirectionDiscardsOldFraction() {
        CanvasHotbarScroll scroll = new CanvasHotbarScroll();

        assertEquals(0, scroll.add(0.0, 0.8));
        assertEquals(0, scroll.add(0.0, -0.4));
        assertEquals(-1, scroll.add(0.0, -0.7));
    }

    @Test
    void resetDiscardsFractionConsumedByAnotherGesture() {
        CanvasHotbarScroll scroll = new CanvasHotbarScroll();

        assertEquals(0, scroll.add(0.0, 0.8));
        scroll.reset();
        assertEquals(0, scroll.add(0.0, 0.4));
    }
}
