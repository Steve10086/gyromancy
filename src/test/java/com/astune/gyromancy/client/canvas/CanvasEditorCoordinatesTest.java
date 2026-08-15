package com.astune.gyromancy.client.canvas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanvasEditorCoordinatesTest {

    @Test
    void editorInputKeepsMatrixColumnOrder() {
        assertEquals(0, CanvasEditorCoordinates.matrixColumnForScreenColumn(0, 4));
        assertEquals(3, CanvasEditorCoordinates.matrixColumnForScreenColumn(3, 4));
    }

    @Test
    void textureOutputMirrorsMatrixColumnOrder() {
        assertEquals(3, CanvasEditorCoordinates.screenColumnForMatrixColumn(0, 4));
        assertEquals(0, CanvasEditorCoordinates.screenColumnForMatrixColumn(3, 4));
    }
}
