package com.astune.gyromancy.api.element;

import com.mojang.serialization.Codec;

import java.util.Arrays;

/**
 * A nine-slot elemental amount produced by the mana-id system.
 *
 * <p>This is deliberately independent from the world element system: the slots
 * reuse the {@link ElementType} names and order but never read or write world
 * element concentrations.
 */
public record ManaElements(double[] values) {
    public static final int SLOT_COUNT = ElementType.COUNT;
    public static final ManaElements EMPTY = new ManaElements(new double[SLOT_COUNT]);

    /** Scratch key under which an activated effect keeps its own copy. */
    public static final String SCRATCH_KEY = "__mana_elements";

    public static final Codec<ManaElements> CODEC = Codec.DOUBLE.listOf().xmap(
            list -> {
                double[] values = new double[list.size()];
                for (int index = 0; index < values.length; index++) values[index] = list.get(index);
                return new ManaElements(values);
            },
            elements -> {
                java.util.List<Double> list = new java.util.ArrayList<>(SLOT_COUNT);
                for (double value : elements.values) list.add(value);
                return list;
            });

    public ManaElements {
        values = Arrays.copyOf(values, SLOT_COUNT);
    }

    @Override
    public double[] values() {
        return values.clone();
    }

    public double at(ElementType type) {
        return values[type.ordinal()];
    }

    public boolean isEmpty() {
        for (double value : values) {
            if (value != 0.0) return false;
        }
        return true;
    }

    public ManaElements plus(ManaElements other) {
        double[] result = new double[SLOT_COUNT];
        for (int index = 0; index < SLOT_COUNT; index++) {
            result[index] = values[index] + other.values[index];
        }
        return new ManaElements(result);
    }

    public ManaElements scaled(double factor) {
        double[] result = new double[SLOT_COUNT];
        for (int index = 0; index < SLOT_COUNT; index++) {
            result[index] = values[index] * factor;
        }
        return new ManaElements(result);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ManaElements other && Arrays.equals(values, other.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }
}
