package com.astune.gyromancy.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.canvas.Carvable;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.item.CarvingMaterials;
import com.astune.gyromancy.registry.ModDataComponents;
import com.astune.gyromancy.registry.ModIngredients;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Vanilla-compatible ingredient that additionally checks a carvable item's
 * persisted rune hierarchy.
 */
public record CarvingIngredient(
        Ingredient item,
        List<ResourceLocation> runes,
        List<CarvingRequirement> outerCircle
) implements ICustomIngredient {
    public static final MapCodec<CarvingIngredient> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Ingredient.CODEC_NONEMPTY.fieldOf("item")
                            .forGetter(CarvingIngredient::item),
                    ResourceLocation.CODEC.listOf()
                            .optionalFieldOf("runes", List.of())
                            .forGetter(CarvingIngredient::runes),
                    CarvingRequirement.CODEC.listOf()
                            .optionalFieldOf("outer_circle", List.of())
                            .forGetter(CarvingIngredient::outerCircle))
                    .apply(instance, CarvingIngredient::new));

    public CarvingIngredient {
        runes = List.copyOf(runes);
        outerCircle = List.copyOf(outerCircle);
    }

    public CarvingRequirement requirement() {
        return new CarvingRequirement(runes, outerCircle);
    }

    @Override
    public boolean test(ItemStack stack) {
        boolean baseMatches = item.test(stack);
        if (!baseMatches) {
            return false;
        }
        if (!(stack.getItem() instanceof Carvable carvable)) {
            if (Gyromancy.LOGGER.isDebugEnabled()) {
                Gyromancy.LOGGER.debug(
                        "[CarvingIngredient] base item matched but is not Carvable: {}",
                        stack.getItem());
            }
            return false;
        }
        CanvasDocument document = carvable.carvingCanvas(stack);
        CarvingRequirement required = requirement();
        boolean matches = CarvingStructureMatcher.matches(document, required);
        if (!matches && Gyromancy.LOGGER.isDebugEnabled()) {
            Gyromancy.LOGGER.debug(
                    "[CarvingIngredient] mismatch item={} glyphs={} arrays={} actualGlyphs={} actualArrays={} requiredRunes={} requiredOuterCircles={}",
                    stack.getItem(),
                    document == null ? -1 : document.glyphs().size(),
                    document == null ? -1 : document.arrays().size(),
                    describeGlyphs(document),
                    describeArrays(document),
                    required.runes(), required.outerCircle().size());
        }
        return matches;
    }

    private static List<String> describeGlyphs(CanvasDocument document) {
        if (document == null) return List.of("<null document>");
        return document.glyphs().stream()
                .map(glyph -> glyph.glyphUuid() + "=" + glyph.symbolId()
                        + "/" + glyph.role())
                .toList();
    }

    private static List<String> describeArrays(CanvasDocument document) {
        if (document == null) return List.of("<null document>");
        Map<java.util.UUID, String> symbols = new HashMap<>();
        document.glyphs().forEach(glyph -> symbols.put(glyph.glyphUuid(),
                glyph.symbolId() + "/" + glyph.role()));
        return document.arrays().stream()
                .map(array -> "root=" + symbols.getOrDefault(array.rootGlyph(),
                                array.rootGlyph().toString())
                        + " bound=" + array.boundGlyphs().stream()
                        .map(id -> symbols.getOrDefault(id, id.toString()))
                        .toList())
                .toList();
    }

    @Override
    public Stream<ItemStack> getItems() {
        CarvingRequirement required = requirement();
        return Arrays.stream(item.getItems())
                .map(stack -> displayStack(stack, required));
    }

    /**
     * Creates the representative stack used by recipe viewers. This document
     * is display-only: crafting still calls {@link #test(ItemStack)} against
     * the real stack supplied by the player.
     */
    private static ItemStack displayStack(ItemStack stack, CarvingRequirement requirement) {
        if (!(stack.getItem() instanceof CarvingMaterials)) return stack;

        ItemStack display = stack.copy();
        display.set(ModDataComponents.CARVING_DOCUMENT.get(),
                CarvingIngredientDisplay.documentFor(requirement));
        return display;
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return ModIngredients.CARVING.get();
    }
}
