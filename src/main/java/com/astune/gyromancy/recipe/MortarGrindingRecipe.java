package com.astune.gyromancy.recipe;

import com.astune.gyromancy.registry.ModItems;
import com.astune.gyromancy.registry.ModRecipes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Grinds one configurable ingredient together with a mortar.
 *
 * <p>The mortar stays in the grid and loses one durability per craft; the last
 * point of durability breaks it, exactly like a damaged tool. Recipes are
 * data-driven: each powder declares its ingredient and result in JSON.</p>
 */
public final class MortarGrindingRecipe extends CustomRecipe {

    private static final Ingredient MORTAR = Ingredient.of(ModItems.MORTAR.get());

    private final Ingredient ingredient;
    private final ItemStack result;

    public MortarGrindingRecipe(CraftingBookCategory category, Ingredient ingredient, ItemStack result) {
        super(category);
        this.ingredient = ingredient;
        this.result = result;
    }

    public Ingredient ingredient() {
        return ingredient;
    }

    public ItemStack result() {
        return result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() != 2) {
            return false;
        }
        boolean mortar = false;
        boolean ground = false;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(ModItems.MORTAR.get())) {
                if (mortar) {
                    return false;
                }
                mortar = true;
            } else if (ingredient.test(stack)) {
                if (ground) {
                    return false;
                }
                ground = true;
            } else {
                return false;
            }
        }
        return mortar && ground;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(ingredient);
        ingredients.add(MORTAR);
        return ingredients;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < remaining.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.is(ModItems.MORTAR.get())) {
                continue;
            }
            ItemStack damaged = stack.copyWithCount(1);
            int damage = damaged.getDamageValue() + 1;
            if (damage < damaged.getMaxDamage()) {
                damaged.setDamageValue(damage);
                remaining.set(i, damaged);
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
        return ModRecipes.MORTAR_GRINDING.get();
    }

    /** Serializer for the ingredient/result driven mortar recipes. */
    public static final class Serializer implements RecipeSerializer<MortarGrindingRecipe> {

        private static final MapCodec<MortarGrindingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        CraftingBookCategory.CODEC
                                .optionalFieldOf("category", CraftingBookCategory.MISC)
                                .forGetter(MortarGrindingRecipe::category),
                        Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(MortarGrindingRecipe::ingredient),
                        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(MortarGrindingRecipe::result)
                ).apply(instance, MortarGrindingRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, MortarGrindingRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        CraftingBookCategory.STREAM_CODEC, MortarGrindingRecipe::category,
                        Ingredient.CONTENTS_STREAM_CODEC, MortarGrindingRecipe::ingredient,
                        ItemStack.STREAM_CODEC, MortarGrindingRecipe::result,
                        MortarGrindingRecipe::new);

        @Override
        public MapCodec<MortarGrindingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, MortarGrindingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
