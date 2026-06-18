package com.astune.gyromancy.api.element;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.biome.Biome;

import java.util.Arrays;

/**
 * Immutable value object holding concentration values and their derivatives for all {@link ElementType}s
 * at a specific coordinate. Contains 10 float concentration values and 10 float derivative values.
 *
 * <p>The derivatives represent the rate of change (Δvalue/Δt) from the previous processing tick.
 * Concentrations are clamped to [0, 1] range where 1.0 represents maximum saturation.</p>
 */
public record ElementConcentrations(float[] values, float[] derivatives) {

    public static final int SIZE = ElementType.COUNT;

    /** All concentrations at zero with zero derivatives */
    public static final ElementConcentrations ZERO = new ElementConcentrations(
            new float[SIZE], new float[SIZE]
    );

    /**
     * Creates new concentrations, validating and clamping values.
     */
    public ElementConcentrations {
        if (values.length != SIZE) {
            throw new IllegalArgumentException("values array must have length " + SIZE);
        }
        if (derivatives.length != SIZE) {
            throw new IllegalArgumentException("derivatives array must have length " + SIZE);
        }
        // Clamp values to [0, 1]
        for (int i = 0; i < SIZE; i++) {
            values[i] = Math.clamp(values[i], 0f, 1f);
        }
    }

    /**
     * Creates concentrations from a single values array, with zero derivatives.
     */
    public static ElementConcentrations ofValues(float[] values) {
        return new ElementConcentrations(values, new float[SIZE]);
    }

    /**
     * Creates concentrations with all elements set to the same value.
     */
    public static ElementConcentrations uniform(float value) {
        float[] v = new float[SIZE];
        Arrays.fill(v, Math.clamp(value, 0f, 1f));
        return new ElementConcentrations(v, new float[SIZE]);
    }

    /** Gets the concentration value for the given element type. */
    public float get(ElementType type) {
        return values[type.ordinal()];
    }

    /** Gets the derivative for the given element type. */
    public float getDerivative(ElementType type) {
        return derivatives[type.ordinal()];
    }

    /**
     * Returns a new instance with the specified element's value replaced.
     */
    public ElementConcentrations withValue(ElementType type, float newValue) {
        float[] newValues = values.clone();
        newValues[type.ordinal()] = Math.clamp(newValue, 0f, 1f);
        return new ElementConcentrations(newValues, derivatives);
    }

    /**
     * Returns a new instance with the specified element's derivative replaced.
     */
    public ElementConcentrations withDerivative(ElementType type, float newDerivative) {
        float[] newDerivatives = derivatives.clone();
        newDerivatives[type.ordinal()] = newDerivative;
        return new ElementConcentrations(values, newDerivatives);
    }

    /**
     * Returns true if all values match the given target (within tolerance).
     */
    public boolean equalsWithin(ElementConcentrations other, float tolerance) {
        for (int i = 0; i < SIZE; i++) {
            if (Math.abs(values[i] - other.values[i]) > tolerance) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates a new instance with each value regressed toward the target by the given rate.
     *
     * @param target the biome-default concentrations to regress toward
     * @param rate   the regression rate per tick (e.g., 0.01 = 1% per tick)
     * @return a new ElementConcentrations with regressed values and updated derivatives
     */
    public ElementConcentrations regressToward(ElementConcentrations target, float rate) {
        float[] newValues = new float[SIZE];
        float[] newDerivatives = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            newValues[i] = values[i] + (target.values[i] - values[i]) * rate;
            // Clamp
            newValues[i] = Math.clamp(newValues[i], 0f, 1f);
            // Compute derivative
            newDerivatives[i] = newValues[i] - values[i];
        }
        return new ElementConcentrations(newValues, newDerivatives);
    }

    /**
     * Diffuses element concentrations by averaging with 8 neighboring positions.
     * Each neighbor contributes equally (1/9 weight per cell in the 3×3 grid).
     *
     * @param neighbors array of exactly 8 neighbor concentrations (any may be null if not overridden)
     * @param biomeDefaults the biome default values to use for null/non-overridden neighbors
     * @return new ElementConcentrations after spatial diffusion
     */
    public ElementConcentrations diffuse(ElementConcentrations[] neighbors, ElementConcentrations biomeDefaults) {
        float[] newValues = new float[SIZE];
        float totalWeight = 1f; // self weight = 1

        // Self contributes 1/9
        for (int i = 0; i < SIZE; i++) {
            newValues[i] = values[i];
        }

        // Neighbors each contribute 1/9
        for (ElementConcentrations neighbor : neighbors) {
            if (neighbor != null) {
                for (int i = 0; i < SIZE; i++) {
                    newValues[i] += neighbor.values[i];
                }
            } else {
                for (int i = 0; i < SIZE; i++) {
                    newValues[i] += biomeDefaults.values[i];
                }
            }
            totalWeight += 1f;
        }

        // Average
        for (int i = 0; i < SIZE; i++) {
            newValues[i] = Math.clamp(newValues[i] / totalWeight, 0f, 1f);
        }

        return new ElementConcentrations(newValues, derivatives);
    }

    /**
     * Applies decay and recovery to move values toward their biome defaults.
     * <ul>
     *   <li>Values above default lose 10% of the excess</li>
     *   <li>Values below default (and above 0) gain 10% of the deficit</li>
     *   <li>Values are clamped to [0, 1]</li>
     * </ul>
     *
     * @param defaults the biome-default concentrations
     * @return new ElementConcentrations after decay/recovery
     */
    public ElementConcentrations decayExcessAndRecoverDeficit(ElementConcentrations defaults) {
        float[] newValues = new float[SIZE];
        float[] newDerivatives = new float[SIZE];

        for (int i = 0; i < SIZE; i++) {
            float current = values[i];
            float target = defaults.values[i];

            if (current > target) {
                // Lose 10% of excess
                float excess = current - target;
                newValues[i] = current - 0.1f * excess;
            } else if (current < target && current > 0f) {
                // Recover 10% of deficit
                float deficit = target - current;
                newValues[i] = current + 0.1f * deficit;
            } else {
                newValues[i] = current;
            }

            // Clamp to [0, 1]
            newValues[i] = Math.clamp(newValues[i], 0f, 1f);

            // Compute derivative
            newDerivatives[i] = newValues[i] - values[i];
        }

        return new ElementConcentrations(newValues, newDerivatives);
    }

    /**
     * Checks whether all element values are close enough to their defaults
     * to be cleaned up from override tracking.
     *
     * <p>An element is "close enough" when {@code |value - default| < default × 0.1}.
     * All 10 elements must satisfy this condition.</p>
     *
     * @param defaults the biome-default concentrations
     * @return true if all elements are close to their defaults and the position can be cleaned up
     */
    public boolean isCloseToDefault(ElementConcentrations defaults) {
        for (int i = 0; i < SIZE; i++) {
            float diff = Math.abs(values[i] - defaults.values[i]);
            float threshold = defaults.values[i] * 0.1f;
            if (diff >= threshold) {
                return false;
            }
        }
        return true;
    }

    // ── Codec for serialization ──

    public static final Codec<float[]> FLOAT_ARRAY_CODEC = Codec.FLOAT
            .listOf()
            .xmap(
                    list -> {
                        float[] arr = new float[list.size()];
                        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
                        return arr;
                    },
                    arr -> {
                        java.util.List<Float> list = new java.util.ArrayList<>(arr.length);
                        for (float f : arr) list.add(f);
                        return list;
                    }
            );

    public static final Codec<ElementConcentrations> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    FLOAT_ARRAY_CODEC.fieldOf("values").forGetter(ElementConcentrations::values),
                    FLOAT_ARRAY_CODEC.fieldOf("derivatives").forGetter(ElementConcentrations::derivatives)
            ).apply(instance, ElementConcentrations::new)
    );

    /**
     * A serializable entry in the element override map.
     */
    public record PosEntry(BlockPos pos, ElementConcentrations concentrations) {
        public static final Codec<PosEntry> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        BlockPos.CODEC.fieldOf("pos").forGetter(PosEntry::pos),
                        ElementConcentrations.CODEC.fieldOf("conc").forGetter(PosEntry::concentrations)
                ).apply(instance, PosEntry::new)
        );
    }

    /**
     * Codec for a HashMap<BlockPos, ElementConcentrations> used in chunk attachment serialization.
     */
    public static Codec<java.util.HashMap<BlockPos, ElementConcentrations>> mapCodec() {
        return PosEntry.CODEC.listOf().xmap(
                list -> {
                    java.util.HashMap<BlockPos, ElementConcentrations> map = new java.util.HashMap<>();
                    for (PosEntry entry : list) {
                        map.put(entry.pos(), entry.concentrations());
                    }
                    return map;
                },
                map -> map.entrySet().stream()
                        .map(e -> new PosEntry(e.getKey(), e.getValue()))
                        .toList()
        );
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ElementConcentrations{");
        for (ElementType t : ElementType.values()) {
            sb.append(t.name()).append("=").append(String.format("%.3f", get(t)));
            sb.append("(Δ").append(String.format("%.3f", getDerivative(t))).append("), ");
        }
        sb.setLength(sb.length() - 2);
        sb.append("}");
        return sb.toString();
    }
}
