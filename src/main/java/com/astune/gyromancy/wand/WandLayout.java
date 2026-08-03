package com.astune.gyromancy.wand;

import java.util.Arrays;

/** Describes the projection planes and capacities offered by one wand type. */
public record WandLayout(int[] slotCapacities, double[] slotOffsets) {
    public static final WandLayout DEFAULT = new WandLayout(
            new int[]{2, 2}, new double[]{1.0, 1.5});

    public WandLayout {
        slotCapacities = slotCapacities.clone();
        slotOffsets = slotOffsets.clone();
        if (slotCapacities.length == 0 || slotCapacities.length != slotOffsets.length) {
            throw new IllegalArgumentException("Wand layout must define matching slots");
        }
        for (int capacity : slotCapacities) {
            if (capacity < 1) throw new IllegalArgumentException("Wand slot capacity must be positive");
        }
        for (double offset : slotOffsets) {
            if (!(offset > 0.0) || !Double.isFinite(offset)) {
                throw new IllegalArgumentException("Wand projection offsets must be positive");
            }
        }
    }

    public int slotCount() {
        return slotCapacities.length;
    }

    public int slotCapacity(int slot) {
        return slotCapacities[slot];
    }

    public double slotOffset(int slot) {
        return slotOffsets[slot];
    }

    public int totalCapacity() {
        return Arrays.stream(slotCapacities).sum();
    }
}
