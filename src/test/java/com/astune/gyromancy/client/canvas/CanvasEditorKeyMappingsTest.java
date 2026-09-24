package com.astune.gyromancy.client.canvas;

import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasEditorKeyMappingsTest {
    @Test
    void undoAndRedoDefaultToControlModifiedKeys() {
        assertTrue(CanvasEditorKeyMappings.undoMapping().isDefault());
        assertTrue(CanvasEditorKeyMappings.redoMapping().isDefault());
        assertEquals(KeyModifier.CONTROL,
                CanvasEditorKeyMappings.undoMapping().getDefaultKeyModifier());
        assertEquals(KeyModifier.CONTROL,
                CanvasEditorKeyMappings.redoMapping().getDefaultKeyModifier());
        assertEquals(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_Z),
                CanvasEditorKeyMappings.undoMapping().getDefaultKey());
        assertEquals(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_R),
                CanvasEditorKeyMappings.redoMapping().getDefaultKey());
    }
}
