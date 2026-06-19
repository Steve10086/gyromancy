package com.astune.gyromancy.api.symbol;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Represents the position of a single mana pixel within a multi-block canvas.
 * Each pixel belongs to a specific CanvasFace on a specific block.
 */
public record PixelPos(
        /** The block this pixel belongs to */
        BlockPos pos,
        /** The face direction of the canvas */
        Direction face,
        /** Pixel x-coordinate on the face (0-15) */
        int x,
        /** Pixel y-coordinate on the face (0-15) */
        int y,
        /** ARGB color value of this pixel */
        int color
) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PixelPos that)) return false;
        return x == that.x && y == that.y && pos.equals(that.pos) && face == that.face;
    }

    @Override
    public int hashCode() {
        int result = pos.hashCode();
        result = 31 * result + face.hashCode();
        result = 31 * result + x;
        result = 31 * result + y;
        return result;
    }
}
