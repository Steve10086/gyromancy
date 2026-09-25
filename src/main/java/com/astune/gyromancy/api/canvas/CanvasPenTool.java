package com.astune.gyromancy.api.canvas;

import com.astune.gyromancy.canvas.CanvasToolSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** An editor tool which can create a freehand canvas stroke. */
public interface CanvasPenTool extends CanvasEditorTool {

    /** The odd editor-space diameter of the circular brush. */
    default int editorBrushDiameter(ItemStack stack, Player player) {
        return CanvasToolSettings.penDiameter(player);
    }

    @Override
    default boolean editorMouseScrolled(EditorContext context,
                                         ItemStack stack,
                                         Player player,
                                         double mouseX,
                                         double mouseY,
                                         double scrollX,
                                         double scrollY) {
        if (!context.isShiftDown() || !context.isOverViewport(mouseX, mouseY)) {
            return false;
        }
        double scroll = scrollY != 0.0 ? scrollY : -scrollX;
        if (scroll == 0.0) return false;
        CanvasToolSettings.adjustPenDiameter(player, scroll);
        return true;
    }

    @Override
    default void renderEditorPreview(EditorContext context,
                                     ItemStack stack,
                                     Player player,
                                     GuiGraphics graphics,
                                     double mouseX,
                                     double mouseY,
                                     int canvasLeft,
                                     int canvasTop,
                                     int canvasWidth,
                                     int canvasHeight) {
        if (!context.isOverCanvas(mouseX, mouseY)
                || context.rasterWidth() <= 0
                || context.rasterHeight() <= 0) {
            return;
        }
        Stroke stroke = canvasStroke(stack, player).orElse(null);
        if (stroke == null) return;

        int[] center = context.canvasPixelAt(mouseX, mouseY);
        int rasterWidth = context.rasterWidth();
        int rasterHeight = context.rasterHeight();
        int[] preview = new int[rasterWidth * rasterHeight];
        int previewColor = 0x80000000 | (stroke.color() & 0x00FFFFFF);
        drawBrush(center[0], center[1], editorBrushDiameter(stack, player),
                (x, y) -> {
                    if (x >= 0 && x < rasterWidth && y >= 0 && y < rasterHeight) {
                        preview[y * rasterWidth + x] = previewColor;
                    }
                });
        context.renderPreview(graphics, preview, canvasLeft, canvasTop,
                canvasWidth, canvasHeight);
    }

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
        context.beginToolAction(button);
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
        int diameter = stack.getItem() instanceof CanvasPenTool pen
                ? pen.editorBrushDiameter(stack, player)
                : 1;
        context.visitLine(start[0], start[1], end[0], end[1],
                (x, y) -> drawBrush(x, y, diameter,
                        (brushX, brushY) -> context.writePixel(
                                brushX,
                                brushY,
                                button == 0 ? stroke.color() : 0,
                                button == 0 ? stroke.effect() : 0)));
    }

    private static void drawBrush(int centerX,
                                   int centerY,
                                   int requestedDiameter,
                                   EditorContext.PixelVisitor visitor) {
        int diameter = Math.max(1, requestedDiameter | 1);
        int radius = diameter / 2;
        int radiusSquared = radius * radius;
        for (int offsetY = -radius; offsetY <= radius; offsetY++) {
            for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                if (offsetX * offsetX + offsetY * offsetY > radiusSquared) continue;
                visitor.accept(centerX + offsetX, centerY + offsetY);
            }
        }
    }

    private static Stroke canvasStrokeForAction(ItemStack stack, Player player) {
        return stack.getItem() instanceof CanvasPenTool pen
                ? pen.canvasStroke(stack, player).orElse(null)
                : null;
    }

    record Stroke(int color, int effect) {
        public Stroke {
            if (effect <= 0) {
                throw new IllegalArgumentException(
                        "Canvas stroke effect must be positive");
            }
        }
    }
}
