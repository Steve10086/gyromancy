package com.astune.gyromancy.api.canvas;

import com.astune.gyromancy.item.StampItem;
import com.astune.gyromancy.registry.ModDataComponents;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Public entry point for supplying the material rendered by a stamp canvas. */
public final class StampCanvasApi {
    private StampCanvasApi() {}

    public static StampCanvasMaterial material(ItemStack stamp) {
        requireStamp(stamp);
        return stamp.getOrDefault(
                ModDataComponents.STAMP_MATERIAL.get(),
                StampCanvasMaterial.DEFAULT);
    }

    public static void setMaterial(ItemStack stamp,
                                   StampCanvasMaterial material) {
        requireStamp(stamp);
        stamp.set(
                ModDataComponents.STAMP_MATERIAL.get(),
                Objects.requireNonNull(material, "material"));
    }

    private static void requireStamp(ItemStack stack) {
        if (!(stack.getItem() instanceof StampItem)) {
            throw new IllegalArgumentException(
                    "Stamp canvas material can only be applied to a stamp item");
        }
    }
}
