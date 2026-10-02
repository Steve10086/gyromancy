package com.astune.gyromancy.recipe;

import com.astune.gyromancy.registry.ModRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Objects;

/**
 * One-shot crush recipe: a single input material is pulverized into one or
 * more results when the crushing level at the stack's position is high enough.
 *
 * <p>Results are defined per input item; the crush effect multiplies them by
 * the consumed stack size and drops them where the input entity stood.</p>
 */
public final class CrushRecipe implements Recipe<SingleRecipeInput> {
    public static final int MAX_LEVEL = 64;

    private final Ingredient ingredient;
    private final List<ItemStack> results;
    private final int requiredLevel;

    public CrushRecipe(Ingredient ingredient, List<ItemStack> results, int requiredLevel) {
        this.ingredient = Objects.requireNonNull(ingredient, "ingredient");
        if (results == null || results.isEmpty()
                || results.stream().anyMatch(result -> result == null || result.isEmpty())) {
            throw new IllegalArgumentException("Crush recipes need at least one non-empty result");
        }
        this.results = results.stream().map(ItemStack::copy).toList();
        this.requiredLevel = Math.clamp(requiredLevel, 1, MAX_LEVEL);
    }

    /** The single input material this recipe consumes. */
    public Ingredient ingredient() {
        return ingredient;
    }

    /** Copies of the products of one crushed input item. */
    public List<ItemStack> results() {
        return results.stream().map(ItemStack::copy).toList();
    }

    /** Minimum crushing level at the input's position. */
    public int requiredLevel() {
        return requiredLevel;
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return !input.isEmpty() && ingredient.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return results.getFirst().copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return results.getFirst().copy();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.CRUSH_RECIPE.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.CRUSH_TYPE.get();
    }

    /** Serializer for ingredient/results/level driven crush recipes. */
    public static final class Serializer implements RecipeSerializer<CrushRecipe> {

        private static final MapCodec<CrushRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        Ingredient.CODEC_NONEMPTY.fieldOf("ingredient")
                                .forGetter(CrushRecipe::ingredient),
                        ItemStack.STRICT_CODEC.listOf().fieldOf("results")
                                .forGetter(CrushRecipe::results),
                        Codec.intRange(1, MAX_LEVEL).fieldOf("level")
                                .forGetter(CrushRecipe::requiredLevel)
                ).apply(instance, CrushRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, CrushRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        Ingredient.CONTENTS_STREAM_CODEC, CrushRecipe::ingredient,
                        ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), CrushRecipe::results,
                        ByteBufCodecs.VAR_INT, CrushRecipe::requiredLevel,
                        CrushRecipe::new);

        @Override
        public MapCodec<CrushRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CrushRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
