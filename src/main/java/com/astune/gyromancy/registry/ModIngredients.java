package com.astune.gyromancy.registry;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.recipe.CarvingIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Custom Ingredient types used by Gyromancy recipes. */
public final class ModIngredients {
    private ModIngredients() {}

    public static final DeferredRegister<IngredientType<?>> INGREDIENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, Gyromancy.MODID);

    public static final Supplier<IngredientType<CarvingIngredient>> CARVING =
            INGREDIENT_TYPES.register("carving", () -> new IngredientType<>(CarvingIngredient.CODEC));
}
