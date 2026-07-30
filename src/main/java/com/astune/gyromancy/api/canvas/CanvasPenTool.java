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

    record Stroke(int color, int effect) {
        public Stroke {
            if (effect <= 0 || effect > 255) {
                throw new IllegalArgumentException(
                        "Canvas stroke effect must be between 1 and 255");
            }
        }
    }
}
