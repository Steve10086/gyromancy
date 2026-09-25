package com.astune.gyromancy.api.element;

/**
 * Represents the eight elemental types plus Mana in the Gyromancy elemental system.
 * Each coordinate in the world has latent concentration values for all nine types,
 * which vary by biome and can be modified by magical arrays.
 */
public enum ElementType {
    /** Wind element — associated with movement, speed, and air biomes */
    WIND("wind", 0xFFA8E8C0),
    /** Fire element — associated with destruction, heat, and volcanic biomes */
    FIRE("fire", 0xFFFF7A33),
    /** Water element - associated with fluids, poison, and wet biomes */
    WATER("water", 0xFF4FA8E8),
    /** Earth element — associated with stability, protection, and underground biomes */
    EARTH("earth", 0xFFB08050),
    /** Light element — associated with purification, healing, and high-altitude biomes */
    LIGHT("light", 0xFFFFF0C0),
    /** Dark element — associated with void, shadows, and deep underground biomes */
    DARK("dark", 0xFF8A6BE0),
    /** Space element — associated with teleportation, storage, and dimensional biomes */
    SPACE("space", 0xFF6FA8DC),
    /** Time element — associated with speed alteration, regeneration, and ancient biomes */
    TIME("time", 0xFFE0C86A),
    /** Mana — raw magical energy, the universal power source for all magical effects */
    MANA("mana", 0xFFA46BFF);

    /** Total number of element types including Mana */
    public static final int COUNT = values().length;

    /** Translation key suffix for this element */
    private final String key;

    /** ARGB tint used by this element's particles and effects */
    private final int color;

    ElementType(String key, int color) {
        this.key = key;
        this.color = color;
    }

    /** Returns the translation key for this element, e.g. "element.gyromancy.wind" */
    public String getTranslationKey() {
        return "element.gyromancy." + key;
    }

    /** Returns the ARGB tint shared by this element's effects. */
    public int color() {
        return color;
    }

    /** Returns the element type at the given index (wraps around) */
    public static ElementType byIndex(int index) {
        return values()[Math.floorMod(index, COUNT)];
    }
}
