package com.astune.gyromancy.recipe;

import com.astune.gyromancy.item.InkBottleItem;
import com.astune.gyromancy.registry.GyromancyRegistries;
import com.astune.gyromancy.registry.ModItems;
import com.astune.gyromancy.registry.ModRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.RecipeMatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Fills or refills an ink bottle with a registered ink.
 *
 * <p>The bottle may be empty or already hold any ink. Matching the recipe's ink
 * stacks charges up to {@link InkBottleItem#MAX_INK}; a different ink (or an
 * empty bottle) is replaced by the recipe's amount. The bottle itself is
 * consumed like any ingredient and the crafted stack carries the new contents.</p>
 */
public final class InkMixingRecipe extends CustomRecipe {

    private static final Ingredient BOTTLE = Ingredient.of(ModItems.INK_BOTTLE.get());
    private static final Codec<ResourceLocation> INK_CODEC = ResourceLocation.CODEC.validate(id ->
            GyromancyRegistries.INK.containsKey(id)
                    ? DataResult.success(id)
                    : DataResult.error(() -> "Unknown ink type " + id));

    private final ResourceLocation ink;
    private final int amount;
    private final NonNullList<Ingredient> ingredients;

    public InkMixingRecipe(CraftingBookCategory category, ResourceLocation ink, int amount,
                           NonNullList<Ingredient> ingredients) {
        super(category);
        this.ink = ink;
        this.amount = amount;
        this.ingredients = ingredients;
    }

    public ResourceLocation ink() {
        return ink;
    }

    public int amount() {
        return amount;
    }

    public List<Ingredient> ingredientList() {
        return ingredients;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() != 1 + ingredients.size()) {
            return false;
        }
        List<ItemStack> rest = new ArrayList<>(ingredients.size());
        boolean bottle = false;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (!bottle && stack.is(ModItems.INK_BOTTLE.get())) {
                bottle = true;
                continue;
            }
            rest.add(stack);
        }
        return bottle && RecipeMatcher.findMatches(rest, ingredients) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        for (ItemStack stack : input.items()) {
            if (stack.is(ModItems.INK_BOTTLE.get())) {
                return InkBottleItem.mix(stack, ink, amount);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return InkBottleItem.createFilled(ink, Math.min(amount, InkBottleItem.MAX_INK));
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> all = NonNullList.create();
        all.add(BOTTLE);
        all.addAll(ingredients);
        return all;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1 + ingredients.size();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.INK_MIXING.get();
    }

    /** Serializer for the ink id bound mixing recipes. */
    public static final class Serializer implements RecipeSerializer<InkMixingRecipe> {

        private static final MapCodec<InkMixingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        CraftingBookCategory.CODEC
                                .optionalFieldOf("category", CraftingBookCategory.MISC)
                                .forGetter(InkMixingRecipe::category),
                        INK_CODEC.fieldOf("ink").forGetter(InkMixingRecipe::ink),
                        Codec.intRange(1, InkBottleItem.MAX_INK)
                                .fieldOf("amount").forGetter(InkMixingRecipe::amount),
                        Ingredient.CODEC_NONEMPTY.listOf()
                                .fieldOf("ingredients").forGetter(InkMixingRecipe::ingredientList)
                ).apply(instance, (category, ink, amount, ingredients) ->
                        new InkMixingRecipe(category, ink, amount, NonNullList.copyOf(ingredients))));

        private static final StreamCodec<RegistryFriendlyByteBuf, InkMixingRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        CraftingBookCategory.STREAM_CODEC, InkMixingRecipe::category,
                        ResourceLocation.STREAM_CODEC, InkMixingRecipe::ink,
                        ByteBufCodecs.VAR_INT, InkMixingRecipe::amount,
                        Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()),
                        InkMixingRecipe::ingredientList,
                        (category, ink, amount, ingredients) ->
                                new InkMixingRecipe(category, ink, amount, NonNullList.copyOf(ingredients)));

        @Override
        public MapCodec<InkMixingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, InkMixingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
