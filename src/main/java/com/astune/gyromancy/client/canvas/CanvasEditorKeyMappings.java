package com.astune.gyromancy.client.canvas;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/** Configurable controls used only while the canvas editor GUI is open. */
public final class CanvasEditorKeyMappings {
    private static final String KEY_CATEGORY = "key.categories.gyromancy";

    private static final KeyMapping VIEW_MODIFIER = new KeyMapping(
            "key.gyromancy.canvas_view_modifier",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_CONTROL,
            KEY_CATEGORY
    );

    private static final KeyMapping UNDO = new KeyMapping(
            "key.gyromancy.canvas_undo",
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            KEY_CATEGORY
    );

    private static final KeyMapping REDO = new KeyMapping(
            "key.gyromancy.canvas_redo",
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            KEY_CATEGORY
    );

    private CanvasEditorKeyMappings() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(VIEW_MODIFIER);
        event.register(UNDO);
        event.register(REDO);
    }

    static boolean isViewModifierDown() {
        return VIEW_MODIFIER.isDown();
    }

    static boolean matchesViewModifier(int keyCode, int scanCode) {
        return VIEW_MODIFIER.matches(keyCode, scanCode);
    }

    static boolean usesDefaultViewModifier() {
        return VIEW_MODIFIER.isDefault();
    }

    /** Whether the undo binding (Ctrl+Z by default) was pressed in this GUI. */
    static boolean matchesUndo(int keyCode, int scanCode) {
        return UNDO.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode));
    }

    /** Whether the redo binding (Ctrl+R by default) was pressed in this GUI. */
    static boolean matchesRedo(int keyCode, int scanCode) {
        return REDO.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode));
    }

    static KeyMapping undoMapping() {
        return UNDO;
    }

    static KeyMapping redoMapping() {
        return REDO;
    }
}
