package com.astune.gyromancy.symbol;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.SymbolRole;
import net.minecraft.resources.ResourceLocation;

/**
 * Abstract base for all magic-array symbols.
 * Each symbol owns its name, feature-point count, rotation flag, matching
 * thresholds, glyph color, and role. Built-in symbols are registered in
 * {@link com.astune.gyromancy.registry.ModSymbols}.
 */
public abstract class Symbol {

    private static final String DIR = "/assets/gyromancy/textures/symbol/";

    private final String name;
    private final int featurePoints;
    private final boolean allowRotation;
    private final SkeletonMatcher.SoftThresholds thresholds;
    private final int glyphColor;

    /** Full constructor — all properties explicit. */
    protected Symbol(String name, int featurePoints, boolean allowRotation,
                     SkeletonMatcher.SoftThresholds thresholds, int glyphColor) {
        this.name = name;
        this.featurePoints = featurePoints;
        this.allowRotation = allowRotation;
        this.thresholds = thresholds;
        this.glyphColor = glyphColor;
    }

    /** Defaults thresholds + white glyph. */
    protected Symbol(String name, int featurePoints, boolean allowRotation) {
        this(name, featurePoints, allowRotation, SkeletonMatcher.DEFAULT_THRESHOLDS, 0xFFFFFFFF);
    }

    /** Defaults thresholds, explicit color. */
    protected Symbol(String name, int featurePoints, boolean allowRotation, int glyphColor) {
        this(name, featurePoints, allowRotation, SkeletonMatcher.DEFAULT_THRESHOLDS, glyphColor);
    }

    /** Defaults featurePoints + rotation, explicit thresholds. */
    protected Symbol(String name, SkeletonMatcher.SoftThresholds thresholds) {
        this(name, 0, false, thresholds, 0xFFFFFFFF);
    }

    // ── Getters ──

    public String name() { return name; }
    public int featurePoints() { return featurePoints; }
    public boolean allowRotation() { return allowRotation; }
    public abstract SymbolRole role();
    public SkeletonMatcher.SoftThresholds thresholds() { return thresholds; }
    public int glyphColor() { return glyphColor; }

    /** Namespaced identifier: {@code gyromancy:<name>}. */
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, name);
    }

    /** Classpath to the template PNG: {@code /assets/gyromancy/textures/symbol/<name>.png}. */
    public String resourcePath() {
        return DIR + name + ".png";
    }
}
