package com.astune.gyromancy.api.ink;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * Pure merge rules for ink bottle contents.
 *
 * <p>Mixing the same ink into a bottle stacks charges up to {@code max};
 * mixing an empty bottle or a different ink starts the recipe's amount and
 * replaces whatever was stored before.</p>
 */
public final class InkContents {

    private InkContents() {}

    /**
     * Returns the remaining charges after a mixing recipe is crafted into a
     * bottle that currently holds {@code currentInk} with {@code currentRemaining}.
     *
     * @param currentInk      ink stored in the bottle, or null when empty
     * @param currentRemaining charges left in the bottle
     * @param recipeInk       ink produced by the recipe
     * @param amount          charges granted per craft
     * @param max             bottle capacity
     */
    public static int mix(@Nullable ResourceLocation currentInk, int currentRemaining,
                          ResourceLocation recipeInk, int amount, int max) {
        int added = Math.clamp(amount, 0, max);
        if (currentInk != null && currentInk.equals(recipeInk)) {
            return Math.min(max, Math.max(0, currentRemaining) + added);
        }
        return added;
    }
}
