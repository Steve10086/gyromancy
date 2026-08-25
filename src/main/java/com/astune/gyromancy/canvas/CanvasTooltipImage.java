package com.astune.gyromancy.canvas;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/** Visual tooltip payload for a non-empty portable canvas. */
public record CanvasTooltipImage(CanvasDocument document) implements TooltipComponent {}
