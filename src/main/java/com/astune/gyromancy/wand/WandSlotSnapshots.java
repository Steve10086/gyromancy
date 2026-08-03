package com.astune.gyromancy.wand;

import com.mojang.serialization.Codec;

import java.util.ArrayList;
import java.util.List;

/** Persistent wand-owned snapshots indexed by logical projection slot. */
public record WandSlotSnapshots(List<WandSlotSnapshot> slots) {
    public static final WandSlotSnapshots EMPTY = new WandSlotSnapshots(List.of());
    public static final Codec<WandSlotSnapshots> CODEC = WandSlotSnapshot.CODEC.listOf()
            .xmap(WandSlotSnapshots::new, WandSlotSnapshots::slots);

    public WandSlotSnapshots {
        slots = List.copyOf(slots);
    }

    public WandSlotSnapshot get(int slot) {
        return slot >= 0 && slot < slots.size() ? slots.get(slot) : WandSlotSnapshot.EMPTY;
    }

    public WandSlotSnapshots withSize(int size) {
        if (size < 0) throw new IllegalArgumentException("Wand slot count cannot be negative");
        if (slots.size() == size) return this;
        ArrayList<WandSlotSnapshot> resized = new ArrayList<>(size);
        for (int slot = 0; slot < size; slot++) resized.add(get(slot));
        return new WandSlotSnapshots(resized);
    }
}
