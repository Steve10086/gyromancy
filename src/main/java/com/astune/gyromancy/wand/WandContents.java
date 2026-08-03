package com.astune.gyromancy.wand;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** The canvas stacks stored by a wand. Empty entries preserve slot positions. */
public record WandContents(List<ItemStack> stacks) {
    public static final WandContents EMPTY = new WandContents(List.of());

    public static final Codec<WandContents> CODEC = ItemStack.OPTIONAL_CODEC.listOf()
            .xmap(WandContents::new, WandContents::stacks);

    public static final StreamCodec<RegistryFriendlyByteBuf, WandContents> STREAM_CODEC =
            ItemStack.OPTIONAL_LIST_STREAM_CODEC.map(WandContents::new, WandContents::stacks);

    public WandContents {
        stacks = stacks.stream().map(ItemStack::copy).toList();
    }

    public ItemStack get(int slot) {
        return slot >= 0 && slot < stacks.size() ? stacks.get(slot).copy() : ItemStack.EMPTY;
    }

    public WandContents withSize(int size) {
        if (size < 0) throw new IllegalArgumentException("Wand slot count cannot be negative");
        if (stacks.size() == size) return this;
        java.util.ArrayList<ItemStack> resized = new java.util.ArrayList<>(size);
        for (int index = 0; index < size; index++) resized.add(get(index));
        return new WandContents(resized);
    }

    public WandContents with(int slot, ItemStack stack) {
        java.util.ArrayList<ItemStack> next = new java.util.ArrayList<>(stacks);
        while (next.size() <= slot) next.add(ItemStack.EMPTY);
        next.set(slot, stack.copy());
        return new WandContents(next);
    }
}
