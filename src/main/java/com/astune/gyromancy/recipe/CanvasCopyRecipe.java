package com.astune.gyromancy.recipe;

import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.registry.ModDataComponents;
import com.astune.gyromancy.registry.ModItems;
import com.astune.gyromancy.registry.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Copies a canvas document onto a fresh one-block canvas without consuming the source. */
public final class CanvasCopyRecipe extends CustomRecipe {
    public CanvasCopyRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() != 2) return false;

        boolean canvas = false;
        boolean paper = false;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.is(ModItems.CANVAS.get())) {
                if (canvas) return false;
                canvas = true;
            } else if (stack.is(Items.PAPER)) {
                if (paper) return false;
                paper = true;
            } else if (!stack.isEmpty()) {
                return false;
            }
        }
        return canvas && paper;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.is(ModItems.CANVAS.get())) continue;

            CanvasDocument document = stack.getOrDefault(
                    ModDataComponents.CANVAS_DOCUMENT.get(), CanvasDocument.blank(1, 1));
            ItemStack result = new ItemStack(ModItems.CANVAS.get());
            result.set(ModDataComponents.CANVAS_DOCUMENT.get(), document.duplicateAsSingleBlock());
            return result;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.is(ModItems.CANVAS.get())) {
                remaining.set(i, stack.copyWithCount(1));
                break;
            }
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.CANVAS_COPY.get();
    }
}
