package com.astune.gyromancy.element;

import com.astune.gyromancy.api.element.ElementConcentrations;
import com.astune.gyromancy.api.element.ElementType;
import net.minecraft.world.level.biome.Biome;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps biome types to their default element concentration profiles.
 * Default values are in the int32 range (clamped to {@link Integer#MAX_VALUE}).
 */
public final class ElementBiomeProvider {

    private ElementBiomeProvider() {}

    private static final Map<Biome, ElementConcentrations> cache = new ConcurrentHashMap<>();

    /**
     * Returns the default element concentrations for the given biome.
     */
    public static ElementConcentrations getDefault(Biome biome) {
        return cache.computeIfAbsent(biome, ElementBiomeProvider::compute);
    }

    private static ElementConcentrations compute(Biome biome) {
        float temp = biome.getBaseTemperature();
        float downfall = biome.getModifiedClimateSettings().downfall();
        boolean isUnderground = temp < 0.15f && downfall < 0.1f;

        long[] values = new long[ElementType.COUNT];

        values[ElementType.WIND.ordinal()]  = scale(0.2f + (1f - temp) * 0.3f);
        values[ElementType.FIRE.ordinal()]  = scale(temp * 0.8f);
        values[ElementType.WOOD.ordinal()]  = scale(downfall * 0.9f + 0.05f);
        values[ElementType.EARTH.ordinal()] = scale(isUnderground ? 0.6f : 0.4f);
        values[ElementType.LIGHT.ordinal()] = scale(isUnderground ? 0.05f : temp * 0.6f + 0.1f);
        values[ElementType.DARK.ordinal()]  = scale(isUnderground ? 0.75f : 0.6f - temp * 0.5f);
        values[ElementType.SPACE.ordinal()] = scale(Math.abs(temp - 0.5f) * 0.3f + 0.05f);
        values[ElementType.TIME.ordinal()]  = scale(0.1f);
        values[ElementType.MANA.ordinal()]  = scale(0.1f + downfall * 0.3f);

        return new ElementConcentrations(values, new long[ElementType.COUNT]);
    }

    /** Scales a [0,1] fraction to a sane default magnitude in the int32 range. */
    private static long scale(float fraction) {
        float clamped = Math.clamp(fraction, 0f, 1f);
        return (long) (clamped * 1000f);
    }
}
