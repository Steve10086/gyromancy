package com.astune.gyromancy.canvas;

import com.astune.gyromancy.symbol.FloodFillExtractor;
import com.astune.gyromancy.symbol.FloodFillExtractor.ExtractedGlyph;
import com.astune.gyromancy.util.CanvasScanUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CanvasGlyphExtractionTest {
    @Test
    void serverRecognitionUsesTheTightComponentMatrixAtEveryResolution() {
        CanvasScanUtils.ConnectedComponent component =
                new CanvasScanUtils.ConnectedComponent(
                        new int[][]{
                                {1, 1, 1},
                                {1, 0, 1}
                        },
                        4, 6, 6, 7,
                        5.0, 6.4, 5);

        for (int scale : new int[]{1, 3, CanvasDocument.MAX_RESOLUTION_SCALE}) {
            CanvasDocument document = CanvasDocument.blank(1, 1).resample(scale);
            int width = document.resolutionWidth();
            int[] cells = {
                    6 * width + 4, 6 * width + 5, 6 * width + 6,
                    7 * width + 4, 7 * width + 6
            };

            ExtractedGlyph glyph = CanvasCompileService.extractedGlyph(
                    document, component, cells);
            int[][] raw = FloodFillExtractor.rawGlyphMatrix(glyph);

            assertEquals(2, raw.length, "scale " + scale);
            assertEquals(3, raw[0].length, "scale " + scale);
            assertArrayEquals(new int[]{1, 1, 1}, raw[0], "scale " + scale);
            assertArrayEquals(new int[]{1, 0, 1}, raw[1], "scale " + scale);
        }
    }
}
