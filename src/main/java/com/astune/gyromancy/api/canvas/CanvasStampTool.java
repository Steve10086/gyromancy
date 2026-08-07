package com.astune.gyromancy.api.canvas;

import com.astune.gyromancy.canvas.CanvasDocument;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** An editor tool which places a complete raster pattern in one action. */
public interface CanvasStampTool extends CanvasEditorTool {

    /**
     * Resolves the pattern supplied by this particular stack.
     *
     * <p>The document uses the canvas editor's top-origin coordinates. Its
     * physical dimensions determine the size of the impression on the target
     * canvas, while its resolution is sampled with nearest-neighbour rules.
     */
    Optional<CanvasDocument> canvasStamp(ItemStack stack, Player player);
}
