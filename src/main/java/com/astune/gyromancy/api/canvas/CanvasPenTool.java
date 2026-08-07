package com.astune.gyromancy.api.canvas;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** An editor tool which can create a freehand canvas stroke. */
public interface CanvasPenTool extends CanvasEditorTool {

    /**
     * Resolves the stroke supplied by this particular stack.
     *
     * <p>An empty result temporarily disables drawing, allowing future pens to
     * depend on durability, ink or other item state.
     */
    Optional<Stroke> canvasStroke(ItemStack stack, Player player);

    @Override
    default boolean editorMouseClicked(EditorContext context,
                                       ItemStack stack,
                                       Player player,
                                       double mouseX,
                                       double mouseY,
                                       int button) {
        if (!context.carriedItemEmpty()
                || !context.isOverCanvas(mouseX, mouseY)
                || (button != 0 && button != 1)) {
            return false;
        }
        Stroke stroke = canvasStroke(stack, player).orElse(null);
        if (stroke == null) return true;

        context.beginHistoryAction();
        context.beginPenStroke(stroke, button);
        paintLine(context, stack, player, mouseX, mouseY, mouseX, mouseY, button);
        context.updateChanged();
        context.updateActionButtons();
        return true;
    }

    @Override
    default boolean editorMouseDragged(EditorContext context,
                                       ItemStack stack,
                                       Player player,
                                       double mouseX,
                                       double mouseY,
                                       int button,
                                       double dragX,
                                       double dragY) {
        if (!context.isToolActionActive()
                || button != context.toolActionButton()) {
            return false;
        }
        if (!context.isOverCanvas(mouseX, mouseY)) return false;
        paintLine(
                context,
                stack,
                player,
                mouseX - dragX,
                mouseY - dragY,
                mouseX,
                mouseY,
                button);
        context.updateChanged();
        context.updateActionButtons();
        return true;
    }

    @Override
    default boolean editorMouseReleased(EditorContext context,
                                        ItemStack stack,
                                        Player player,
                                        double mouseX,
                                        double mouseY,
                                        int button) {
        if (!context.isToolActionActive()
                || (button != 0 && button != 1)) {
            return false;
        }
        context.finishToolAction();
        return true;
    }

    private static void paintLine(EditorContext context,
                                  ItemStack stack,
                                  Player player,
                                  double startMouseX,
                                  double startMouseY,
                                  double endMouseX,
                                  double endMouseY,
                                  int button) {
        Stroke stroke = context.isToolActionActive()
                ? canvasStrokeForAction(stack, player)
                : null;
        if (stroke == null) return;
        int[] start = context.canvasPixelAt(startMouseX, startMouseY);
        int[] end = context.canvasPixelAt(endMouseX, endMouseY);
        context.visitLine(start[0], start[1], end[0], end[1],
                (x, y) -> context.writePixel(
                        x,
                        y,
                        button == 0 ? stroke.color() : 0,
                        button == 0 ? stroke.effect() : 0));
    }

    private static Stroke canvasStrokeForAction(ItemStack stack, Player player) {
        return stack.getItem() instanceof CanvasPenTool pen
                ? pen.canvasStroke(stack, player).orElse(null)
                : null;
    }

    record Stroke(int color, int effect) {
        public Stroke {
            if (effect <= 0 || effect > 255) {
                throw new IllegalArgumentException(
                        "Canvas stroke effect must be between 1 and 255");
            }
        }
    }
}
