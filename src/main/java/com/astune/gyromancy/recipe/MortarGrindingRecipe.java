package com.astune.gyromancy.recipe;

import com.astune.gyromancy.registry.ModItems;
import com.astune.gyromancy.registry.ModRecipes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/**
 * Grinds one configurable ingredient together with a mortar.
 *
 * <p>Extends {@link ShapelessRecipe} so recipe viewers (and Modopedia) can draw
 * it as a normal crafting recipe. The mortar stays in the grid and loses one
 * durability per craft; the last point of durability breaks it, exactly like a
 * damaged tool. Each powder declares its ingredient and result in JSON.</p>
 */
public final class MortarGrindingRecipe extends ShapelessRecipe {

    private static final Ingredient MORTAR = Ingredient.of(ModItems.MORTAR.get());

    private final Ingredient ingredient;
    private final ItemStack previewResult;

    public MortarGrindingRecipe(CraftingBookCategory category, Ingredient ingredient, ItemStack result) {
        super("", category, result, NonNullList.of(Ingredient.EMPTY, ingredient, MORTAR));
        this.ingredient = ingredient;
        this.previewResult = result;
    }

    public Ingredient ingredient() {
        return ingredient;
    }

    public ItemStack result() {
        return previewResult;
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
