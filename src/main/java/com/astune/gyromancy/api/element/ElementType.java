package com.astune.gyromancy.api.element;

/**
 * Represents the eight elemental types plus Mana in the Gyromancy elemental system.
 * Each coordinate in the world has latent concentration values for all nine types,
 * which vary by biome and can be modified by magical arrays.
 */
public enum ElementType {
    /** Wind element — associated with movement, speed, and air biomes */
    WIND("wind"),
    /** Fire element — associated with destruction, heat, and volcanic biomes */
    FIRE("fire"),
    /** Water element - associated with fluids, poison, and wet biomes */
    WATER("water"),
    /** Earth element — associated with stability, protection, and underground biomes */
    EARTH("earth"),
    /** Light element — associated with purification, healing, and high-altitude biomes */
    LIGHT("light"),
    /** Dark element — associated with void, shadows, and deep underground biomes */
    DARK("dark"),
    /** Space element — associated with teleportation, storage, and dimensional biomes */
    SPACE("space"),
    /** Time element — associated with speed alteration, regeneration, and ancient biomes */
    TIME("time"),
    /** Mana — raw magical energy, the universal power source for all magical effects */
    MANA("mana");

    /** Total number of element types including Mana */
    public static final int COUNT = values().length;

    /** Translation key suffix for this element */
    private final String key;

    ElementType(String key) {
        this.key = key;
    }

    /** Returns the translation key for this element, e.g. "element.gyromancy.wind" */
    public String getTranslationKey() {
        return "element.gyromancy." + key;
    }

    /** Returns the element type at the given index (wraps around) */
    public static ElementType byIndex(int index) {
        return values()[Math.floorMod(index, COUNT)];
    }
}
