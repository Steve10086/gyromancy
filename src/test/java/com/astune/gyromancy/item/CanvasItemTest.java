package com.astune.gyromancy.item;

import com.astune.gyromancy.canvas.CanvasDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanvasItemTest {
    @Test
    void onlyBlankCanvasesStack() {
        assertEquals(CanvasItem.EMPTY_STACK_SIZE,
                CanvasItem.stackSizeFor(CanvasDocument.blank(1, 1)));
        assertEquals(CanvasItem.EMPTY_STACK_SIZE,
                CanvasItem.stackSizeFor(CanvasDocument.blank(4, 3)));
        assertEquals(CanvasItem.EMPTY_STACK_SIZE, CanvasItem.stackSizeFor(null));
    }

    @Test
    void canvasesWithRasterContentStayUnstackable() {
        CanvasDocument blank = CanvasDocument.blank(2, 2);
        int[] colors = blank.colors();
        colors[0] = 0xFF112233;
        CanvasDocument painted = blank.withRaster(colors, blank.strokeEffects());

        assertEquals(1, CanvasItem.stackSizeFor(painted));
    }
}
