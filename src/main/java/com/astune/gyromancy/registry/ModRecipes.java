package com.astune.gyromancy.registry;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.recipe.CanvasCopyRecipe;
import com.astune.gyromancy.recipe.CanvasUpgradeRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** Recipe serializers registered by Gyromancy. */
public final class ModRecipes {
    private ModRecipes() {}

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Gyromancy.MODID);

    public static final Supplier<RecipeSerializer<CanvasUpgradeRecipe>> CANVAS_UPGRADE =
            RECIPE_SERIALIZERS.register("canvas_upgrade",
                    () -> new SimpleCraftingRecipeSerializer<>(CanvasUpgradeRecipe::new));

    public static final Supplier<RecipeSerializer<CanvasCopyRecipe>> CANVAS_COPY =
            RECIPE_SERIALIZERS.register("canvas_copy",
                    () -> new SimpleCraftingRecipeSerializer<>(CanvasCopyRecipe::new));
}
