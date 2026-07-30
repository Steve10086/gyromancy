package com.astune.gyromancy.client.canvas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanvasEditorCoordinatesTest {

    @Test
    void screenAndMatrixXAxisAreMirroredAtTheEditorBoundary() {
        assertEquals(3, CanvasEditorCoordinates.matrixColumnForScreenColumn(0, 4));
        assertEquals(0, CanvasEditorCoordinates.matrixColumnForScreenColumn(3, 4));
        assertEquals(3, CanvasEditorCoordinates.screenColumnForMatrixColumn(0, 4));
        assertEquals(0, CanvasEditorCoordinates.screenColumnForMatrixColumn(3, 4));
    }

    @Test
    void conversionRoundTripsEveryColumn() {
        int width = 17;
        for (int column = 0; column < width; column++) {
            int matrix = CanvasEditorCoordinates.matrixColumnForScreenColumn(column, width);
            assertEquals(column,
                    CanvasEditorCoordinates.screenColumnForMatrixColumn(matrix, width));
        }
    }
}
