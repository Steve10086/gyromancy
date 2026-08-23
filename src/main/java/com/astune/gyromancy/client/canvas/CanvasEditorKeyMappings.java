package com.astune.gyromancy.client.canvas;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
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

    private CanvasEditorKeyMappings() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(VIEW_MODIFIER);
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
}
