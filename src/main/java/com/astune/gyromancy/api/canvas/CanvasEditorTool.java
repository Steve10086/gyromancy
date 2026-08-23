package com.astune.gyromancy.api.canvas;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Behaviour owned by an item while it is selected in the canvas editor.
 *
 * <p>Pen, compass and stamp behaviors use separate sub-interfaces so adding a
 * new tool does not require treating every selected inventory item as a pen.
 */
public interface CanvasEditorTool {

    /** Renders the selected item's transient editor overlay. */
    default void renderEditorPreview(EditorContext context,
                                     ItemStack stack,
                                     Player player,
                                     GuiGraphics graphics,
                                     double mouseX,
                                     double mouseY,
                                     int canvasLeft,
                                     int canvasTop,
                                     int canvasWidth,
                                     int canvasHeight) {}

    /** Handles an item-specific mouse press. */
    default boolean editorMouseClicked(EditorContext context,
                                       ItemStack stack,
                                       Player player,
                                       double mouseX,
                                       double mouseY,
                                       int button) {
        return false;
    }

    /** Handles an item-specific drag. */
    default boolean editorMouseDragged(EditorContext context,
                                       ItemStack stack,
                                       Player player,
                                       double mouseX,
                                       double mouseY,
                                       int button,
                                       double dragX,
                                       double dragY) {
        return false;
    }

    /** Handles an item-specific mouse release. */
    default boolean editorMouseReleased(EditorContext context,
                                        ItemStack stack,
                                        Player player,
                                        double mouseX,
                                        double mouseY,
                                        int button) {
        return false;
    }

    /** Handles an item-specific scroll after Ctrl-modified viewport scrolling. */
    default boolean editorMouseScrolled(EditorContext context,
                                         ItemStack stack,
                                         Player player,
                                         double mouseX,
                                         double mouseY,
                                         double scrollX,
                                         double scrollY) {
        return false;
    }

    /** Handles an item-specific key press. */
    default boolean editorKeyPressed(EditorContext context,
                                     ItemStack stack,
                                     Player player,
                                     int keyCode,
                                     int scanCode,
                                     int modifiers) {
        return false;
    }

    /** Handles an item-specific key release. */
    default boolean editorKeyReleased(EditorContext context,
                                      ItemStack stack,
                                      Player player,
                                      int keyCode,
                                      int scanCode,
                                      int modifiers) {
        return false;
    }

    /** Finishes a tool action when focus, selection or the screen changes. */
    default void finishEditorAction(EditorContext context,
                                    ItemStack stack,
                                    Player player) {
        context.finishToolAction();
    }

    /** Context exposed by the screen; no editor implementation lives here. */
    interface EditorContext {
        int entityId();

        int physicalWidth();

        int physicalHeight();

        int rasterWidth();

        int rasterHeight();

        double displayX();

        double displayY();

        double displayWidth();

        double displayHeight();

        boolean isOverViewport(double mouseX, double mouseY);

        boolean isOverCanvas(double mouseX, double mouseY);

        int[] canvasPixelAt(double mouseX, double mouseY);

        boolean carriedItemEmpty();

        void beginHistoryAction();

        void beginToolAction(int button);

        boolean isToolActionActive();

        int toolActionButton();

        void finishToolAction();

        void writePixel(int x, int y, int color, int effect);

        void visitLine(int startX,
                       int startY,
                       int endX,
                       int endY,
                       PixelVisitor visitor);

        void renderPreview(GuiGraphics graphics,
                           int[] pixels,
                           int canvasLeft,
                           int canvasTop,
                           int canvasWidth,
                           int canvasHeight);

        boolean isViewModifierActive();

        boolean isShiftDown();

        void updateChanged();

        void updateActionButtons();

        void invalidateRunePreview();

        @FunctionalInterface
        interface PixelVisitor {
            void accept(int x, int y);
        }
    }
}
