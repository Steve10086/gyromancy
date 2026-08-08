package com.astune.gyromancy.item;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Immutable one-slot contents stored by a compass item. */
public record CompassContents(List<ItemStack> stacks) {
    public static final CompassContents EMPTY = new CompassContents(List.of());

    public static final Codec<CompassContents> CODEC = ItemStack.OPTIONAL_CODEC.listOf()
            .xmap(CompassContents::new, CompassContents::stacks);

    public static final StreamCodec<RegistryFriendlyByteBuf, CompassContents> STREAM_CODEC =
            ItemStack.OPTIONAL_LIST_STREAM_CODEC.map(CompassContents::new, CompassContents::stacks);

    public CompassContents {
        stacks = stacks.stream().map(ItemStack::copy).toList();
    }

    public ItemStack pen() {
        return stacks.isEmpty() ? ItemStack.EMPTY : stacks.get(0).copy();
    }

    public CompassContents withPen(ItemStack pen) {
        return pen.isEmpty() ? EMPTY : new CompassContents(List.of(pen));
    }
}
