package com.astune.gyromancy.api.ink;

import com.astune.gyromancy.api.element.ElementType;
import net.minecraft.resources.ResourceLocation;

/**
 * Defines an ink type used in magic array drawing.
 * Ink determines the color and magical properties of drawn symbols.
 */
public class InkType {

    private final ResourceLocation id;
    private final int color;               // ARGB color
    private final float magicalAffinity;   // Affects array power multiplier
    private final ElementType primaryElement; // Which element this ink resonates with
    private final float elementBoost;      // Multiplier for element concentration effects

    private InkType(Builder builder) {
        this.id = builder.id;
        this.color = builder.color;
        this.magicalAffinity = Math.clamp(builder.magicalAffinity, 0f, 2f);
        this.primaryElement = builder.primaryElement;
        this.elementBoost = Math.clamp(builder.elementBoost, 0f, 2f);
    }

    public ResourceLocation getId() { return id; }
    public int getColor() { return color; }
    public float getMagicalAffinity() { return magicalAffinity; }
    public ElementType getPrimaryElement() { return primaryElement; }
    public float getElementBoost() { return elementBoost; }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public static class Builder {
        private final ResourceLocation id;
        private int color = 0xFF000000;
        private float magicalAffinity = 1.0f;
        private ElementType primaryElement = ElementType.MANA;
        private float elementBoost = 0.0f;

        private Builder(ResourceLocation id) {
            this.id = id;
        }

        public Builder color(int color) { this.color = color; return this; }
        public Builder magicalAffinity(float magicalAffinity) { this.magicalAffinity = magicalAffinity; return this; }
        public Builder primaryElement(ElementType element) { this.primaryElement = element; return this; }
        public Builder elementBoost(float elementBoost) { this.elementBoost = elementBoost; return this; }

        public InkType build() {
            return new InkType(this);
        }
    }
}
