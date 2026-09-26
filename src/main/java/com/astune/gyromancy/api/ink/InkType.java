package com.astune.gyromancy.api.ink;

import com.astune.gyromancy.api.element.ElementType;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Defines an ink type used in magic array drawing.
 * Ink determines the color, mana value, and magical effect layers of drawn symbols.
 */
public class InkType {

    private final ResourceLocation id;
    private final int color;               // ARGB color
    private final float magicalAffinity;   // Affects array power multiplier
    private final ElementType primaryElement; // Which element this ink resonates with
    private final float elementBoost;      // Multiplier for element concentration effects
    private final int manaValue;           // Value written to gyromancy:mana effect layer
    private final Map<String, Integer> effectKeys; // Additional effect layers (e.g. gyromancy:element)
    private final int overlayTint;         // ARGB tint applied to the bottle's ink overlay layer

    private InkType(Builder builder) {
        this.id = builder.id;
        this.color = builder.color;
        this.magicalAffinity = Math.clamp(builder.magicalAffinity, 0f, 2f);
        this.primaryElement = builder.primaryElement;
        this.elementBoost = Math.clamp(builder.elementBoost, 0f, 2f);
        this.manaValue = builder.manaValue;
        this.effectKeys = Collections.unmodifiableMap(new HashMap<>(builder.effectKeys));
        this.overlayTint = builder.overlayTint;
    }

    public ResourceLocation getId() { return id; }
    public int getColor() { return color; }
    public float getMagicalAffinity() { return magicalAffinity; }
    public ElementType getPrimaryElement() { return primaryElement; }
    public float getElementBoost() { return elementBoost; }
    public int getManaValue() { return manaValue; }
    public Map<String, Integer> getEffectKeys() { return effectKeys; }

    /**
     * ARGB color multiplied onto the bottle's ink overlay layer.
     * Use {@code 0xFFFFFFFF} to show a fully colored overlay texture as-is;
     * set the ink color to tint a grayscale overlay.
     */
    public int getOverlayTint() { return overlayTint; }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public static class Builder {
        private final ResourceLocation id;
        private int color = 0xFF000000;
        private float magicalAffinity = 1.0f;
        private ElementType primaryElement = ElementType.MANA;
        private float elementBoost = 0.0f;
        private int manaValue = 10;
        private Map<String, Integer> effectKeys = Collections.emptyMap();
        private int overlayTint = 0xFFFFFFFF;

        private Builder(ResourceLocation id) {
            this.id = id;
        }

        public Builder color(int color) { this.color = color; return this; }
        public Builder magicalAffinity(float magicalAffinity) { this.magicalAffinity = magicalAffinity; return this; }
        public Builder primaryElement(ElementType element) { this.primaryElement = element; return this; }
        public Builder elementBoost(float elementBoost) { this.elementBoost = elementBoost; return this; }
        public Builder manaValue(int manaValue) { this.manaValue = manaValue; return this; }
        public Builder effectKey(String key, int value) {
            if (this.effectKeys.isEmpty()) this.effectKeys = new HashMap<>();
            this.effectKeys.put(key, value);
            return this;
        }

        public Builder overlayTint(int overlayTint) { this.overlayTint = overlayTint; return this; }

        public InkType build() {
            return new InkType(this);
        }
    }
}
