package com.astune.gyromancy.recipe;

import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.registry.ModDataComponents;
import com.astune.gyromancy.registry.ModItems;
import com.astune.gyromancy.registry.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Upgrades a canvas using eight sheets of paper while retaining its complete
 * item data. Only the placed physical size changes; the raster resolution and
 * all canvas-local content remain exactly where they were.
 */
public final class CanvasUpgradeRecipe extends CustomRecipe {
    public CanvasUpgradeRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.width() != 3 || input.height() != 3 || input.ingredientCount() != 9) {
            return false;
        }

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                ItemStack stack = input.getItem(x, y);
                if (x == 1 && y == 1) {
                    if (!stack.is(ModItems.CANVAS.get())) return false;
                    CanvasDocument document = stack.getOrDefault(
                            ModDataComponents.CANVAS_DOCUMENT.get(), CanvasDocument.blank(1, 1));
                    if (!document.canIncreasePhysicalSize()) return false;
                } else if (!stack.is(Items.PAPER)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack source = input.getItem(1, 1);
        CanvasDocument document = source.getOrDefault(
                ModDataComponents.CANVAS_DOCUMENT.get(), CanvasDocument.blank(1, 1));
        if (!source.is(ModItems.CANVAS.get()) || !document.canIncreasePhysicalSize()) {
            return ItemStack.EMPTY;
        }

        ItemStack result = source.copyWithCount(1);
        result.set(ModDataComponents.CANVAS_DOCUMENT.get(), document.increasePhysicalSize());
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.CANVAS_UPGRADE.get();
    }
}
