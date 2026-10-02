package com.astune.gyromancy.recipe;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Requires NeoForge FML Loader bootstrap, unavailable in the plain JUnit test task")
class CrushRecipeTest {

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void matchesOnlyTheConfiguredSingleMaterial() {
        CrushRecipe recipe = new CrushRecipe(Ingredient.of(Items.COBBLESTONE),
                List.of(new ItemStack(Items.GRAVEL)), 1);

        assertTrue(recipe.matches(new SingleRecipeInput(new ItemStack(Items.COBBLESTONE)), null));
        assertFalse(recipe.matches(new SingleRecipeInput(new ItemStack(Items.DIRT)), null));
        assertFalse(recipe.matches(new SingleRecipeInput(ItemStack.EMPTY), null));
        assertEquals(1, recipe.requiredLevel());
        assertEquals(Items.GRAVEL, recipe.results().getFirst().getItem());
    }

    @Test
    void supportsMultipleResultsAndClampsTheRequiredLevel() {
        CrushRecipe recipe = new CrushRecipe(Ingredient.of(Items.GRAVEL),
                List.of(new ItemStack(Items.SAND), new ItemStack(Items.FLINT)), 64);

        assertEquals(2, recipe.results().size());
        assertEquals(64, recipe.requiredLevel());
    }

    @Test
    void rejectsRecipesWithoutResults() {
        assertThrows(IllegalArgumentException.class,
                () -> new CrushRecipe(Ingredient.of(Items.COBBLESTONE), List.of(), 1));
    }
}
