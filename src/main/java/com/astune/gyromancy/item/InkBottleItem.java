package com.astune.gyromancy.item;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.ink.InkContents;
import com.astune.gyromancy.api.ink.InkType;
import com.astune.gyromancy.element.ManaIdTable;
import com.astune.gyromancy.registry.GyromancyRegistries;
import com.astune.gyromancy.registry.ModDataComponents;
import com.astune.gyromancy.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Ink bottle shared by every ink.
 *
 * <p>An empty bottle carries no ink components and cannot paint. A filled
 * bottle stores {@code INK_TYPE} (a {@code gyromancy:ink} registry id) and
 * {@code INK_REMAINING} charges, capped at {@link #MAX_INK}. Consuming the last
 * charge reverts the stack to the empty base state instead of breaking it.</p>
 *
 * <p>Mixing recipes call {@link #mix(ItemStack, ResourceLocation, int)}: the
 * same ink stacks charges, any other ink (or an empty bottle) is replaced by
 * the recipe's amount.</p>
 */
public class InkBottleItem extends Item {

    /** Item-level capacity shared by every ink. */
    public static final int MAX_INK = 6400;

    public InkBottleItem() {
        super(new Properties().stacksTo(1));
    }

    // ═══════════════════════════════════════════════════════════════
    // Contents API
    // ═══════════════════════════════════════════════════════════════

    public static boolean hasInk(ItemStack stack) {
        return getRemaining(stack) > 0 && resolveInk(stack) != null;
    }

    @Nullable
    public static ResourceLocation getInkId(ItemStack stack) {
        return stack.get(ModDataComponents.INK_TYPE.get());
    }

    public static int getRemaining(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.INK_REMAINING.get(), 0);
    }

    @Nullable
    public static InkType resolveInk(ItemStack stack) {
        ResourceLocation id = getInkId(stack);
        return id == null ? null : GyromancyRegistries.INK.get(id);
    }

    /** Creates a filled bottle capped to {@link #MAX_INK}. */
    public static ItemStack createFilled(ResourceLocation inkId, int remaining) {
        ItemStack stack = new ItemStack(ModItems.INK_BOTTLE.get());
        stack.set(ModDataComponents.INK_TYPE.get(), inkId);
        stack.set(ModDataComponents.INK_REMAINING.get(), Math.clamp(remaining, 1, MAX_INK));
        return stack;
    }

    /** Applies a mixing recipe to the bottle, stacking the same ink. */
    public static ItemStack mix(ItemStack bottle, ResourceLocation recipeInk, int amount) {
        ResourceLocation currentInk = bottle.get(ModDataComponents.INK_TYPE.get());
        int remaining = InkContents.mix(
                currentInk, getRemaining(bottle), recipeInk, amount, MAX_INK);
        return createFilled(recipeInk, remaining);
    }

    /** Consumes charges; reaching zero restores the empty base bottle. */
    public static void consume(ItemStack stack, int amount) {
        if (!hasInk(stack)) {
            clearInk(stack);
            return;
        }
        int remaining = getRemaining(stack) - Math.max(0, amount);
        if (remaining <= 0) {
            clearInk(stack);
        } else {
            stack.set(ModDataComponents.INK_REMAINING.get(), remaining);
        }
    }

    public static void clearInk(ItemStack stack) {
        stack.remove(ModDataComponents.INK_TYPE.get());
        stack.remove(ModDataComponents.INK_REMAINING.get());
    }

    /**
     * Item model style: {@code 0} for the empty base bottle, otherwise the
     * ink's registry id plus one so the ink id alone selects the overlay model.
     */
    public static float inkStyleId(ItemStack stack) {
        InkType ink = resolveInk(stack);
        if (ink == null || getRemaining(stack) <= 0) {
            return 0.0F;
        }
        int id = GyromancyRegistries.INK.getId(ink);
        return id < 0 ? 0.0F : id + 1.0F;
    }

    // ═══════════════════════════════════════════════════════════════
    // Durability bar driven by INK_REMAINING
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return hasInk(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int remaining = Math.min(getRemaining(stack), MAX_INK);
        return Math.round(13.0F * remaining / MAX_INK);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        InkType ink = resolveInk(stack);
        return ink == null ? 0xFFFFFFFF : 0xFF000000 | (ink.getColor() & 0x00FFFFFF);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(hasInk(stack)
                ? "item.gyromancy.ink_bottle"
                : "item.gyromancy.ink_bottle.empty");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        InkType ink = resolveInk(stack);
        if (ink == null || getRemaining(stack) <= 0) {
            return;
        }

        tooltip.add(Component.translatable("ink.gyromancy." + ink.getId().getPath()));

        int[] row = ManaIdTable.rowFor(ink.getManaValue());
        ElementType[] elements = ElementType.values();
        MutableComponent values = Component.empty();
        for (int i = 0; i < elements.length; i++) {
            if (i > 0) {
                values.append(Component.literal("|"));
            }
            int value = i < row.length ? row[i] : 0;
            int color = elements[i].color() & 0x00FFFFFF;
            values.append(Component.literal(Integer.toString(value))
                    .withStyle(style -> style.withColor(color)));
        }
        tooltip.add(values);

        tooltip.add(Component.translatable(
                "tooltip.gyromancy.ink_remaining", getRemaining(stack), MAX_INK));
    }
}
