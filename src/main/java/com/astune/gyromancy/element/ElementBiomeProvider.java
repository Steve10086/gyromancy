package com.astune.gyromancy.element;

import com.astune.gyromancy.api.element.ElementConcentrations;
import com.astune.gyromancy.api.element.ElementType;
import net.minecraft.world.level.biome.Biome;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps biome types to their default element concentration profiles.
 *
 * <p>Defaults are computed lazily from biome properties (temperature, downfall, etc.)
 * and cached. A future phase will add data-driven JSON loading for precise tuning.</p>
 */
public final class ElementBiomeProvider {

    private ElementBiomeProvider() {}

    /** Cache for computed biome defaults */
    private static final Map<Biome, ElementConcentrations> cache = new ConcurrentHashMap<>();

    /**
     * Returns the default element concentrations for the given biome.
     * Computes from biome properties and caches the result.
     */
    public static ElementConcentrations getDefault(Biome biome) {
        return cache.computeIfAbsent(biome, ElementBiomeProvider::compute);
    }

    /**
     * Computes default concentrations from a biome's properties.
     * Different biomes receive different elemental profiles based on temperature,
     * downfall, and other climate factors.
     */
    private static ElementConcentrations compute(Biome biome) {
        float temp = biome.getBaseTemperature();
        float downfall = biome.getModifiedClimateSettings().downfall();
        boolean isUnderground = temp < 0.15f && downfall < 0.1f; // rough heuristic

        float[] values = new float[ElementType.COUNT];

        // Wind — moderate everywhere, higher in cold/open areas
        values[ElementType.WIND.ordinal()] = Math.clamp(0.2f + (1f - temp) * 0.3f, 0.05f, 0.85f);
        // Fire — scales strongly with temperature
        values[ElementType.FIRE.ordinal()] = Math.clamp(temp * 0.8f, 0.01f, 0.95f);
        // Wood — scales with rainfall/vegetation
        values[ElementType.WOOD.ordinal()] = Math.clamp(downfall * 0.9f + 0.05f, 0.02f, 0.9f);
        // Earth — moderate, higher underground
        values[ElementType.EARTH.ordinal()] = isUnderground ? 0.6f : 0.4f;
        // Light — scales with temperature (surface biomes are brighter)
        values[ElementType.LIGHT.ordinal()] = isUnderground ? 0.05f : Math.clamp(temp * 0.6f + 0.1f, 0.05f, 0.75f);
        // Dark — inverse of light, higher underground and in cold/damp areas
        values[ElementType.DARK.ordinal()] = isUnderground ? 0.75f : Math.clamp(0.6f - temp * 0.5f, 0.05f, 0.85f);
        // Space — generally low, slightly higher in extreme biomes
        values[ElementType.SPACE.ordinal()] = Math.clamp(Math.abs(temp - 0.5f) * 0.3f + 0.05f, 0.05f, 0.4f);
        // Time — generally low, slightly higher in ancient/stable biomes
        values[ElementType.TIME.ordinal()] = 0.1f;
        // Mana — ambient magical energy, higher in lush or mystical biomes
        values[ElementType.MANA.ordinal()] = Math.clamp(0.1f + downfall * 0.3f, 0.05f, 0.5f);

        return new ElementConcentrations(values, new float[ElementType.COUNT]);
    }
}
