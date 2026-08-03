package com.astune.gyromancy.client.canvas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanvasEntityRendererTest {
    @Test
    void projectionUvCancelsTheEntityTexturesHorizontalMirror() {
        assertEquals(1.0F, CanvasEntityRenderer.projectionTextureU(-1.0F));
        assertEquals(0.0F, CanvasEntityRenderer.projectionTextureU(1.0F));
    }
}
