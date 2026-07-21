package com.astune.gyromancy.array.compile;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ArrayEffectRegistry {
    private static final List<ArrayEffectDefinition> EFFECTS = new ArrayList<>();

    static {
        register(ProjectileElementEffect.FIRE);
        register(ProjectileElementEffect.WATER);
        register(ProjectileElementEffect.MANA);
        assertNoOverlappingSymbols(EFFECTS);
    }

    private ArrayEffectRegistry() {}

    public static List<ArrayEffectDefinition> effects() {
        return List.copyOf(EFFECTS);
    }

    public static void assertNoOverlappingSymbols(Collection<ArrayEffectDefinition> effects) {
        Map<String, ResourceLocation> ownerBySymbol = new HashMap<>();
        for (ArrayEffectDefinition effect : effects) {
            for (String symbol : effect.symbols()) {
                ResourceLocation previous = ownerBySymbol.putIfAbsent(symbol, effect.id());
                if (previous != null) {
                    throw new IllegalStateException("Array effect symbol overlap: " + symbol
                            + " owned by " + previous + " and " + effect.id());
                }
            }
        }
    }

    private static void register(ArrayEffectDefinition effect) {
        EFFECTS.add(effect);
    }

    private record ProjectileElementEffect(
            ResourceLocation id,
            List<String> symbols,
            ElementType element
    ) implements ArrayEffectDefinition {
        private static final ProjectileElementEffect FIRE = new ProjectileElementEffect(
                ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "fire_projectile"),
                List.of("fire"),
                ElementType.FIRE);
        private static final ProjectileElementEffect WATER = new ProjectileElementEffect(
                ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "water_projectile"),
                List.of("water"),
                ElementType.WATER);
        private static final ProjectileElementEffect MANA = new ProjectileElementEffect(
                ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "mana_projectile"),
                List.of("mana"),
                ElementType.MANA);

        @Override
        public ElementType primaryElement(String symbol) {
            return element;
        }

        @Override
        public CompiledArrayNode compile(PositionedGlyph boundary,
                                         ElementType primaryElement,
                                         EffectAttributes attributes,
                                         List<CompiledArrayNode> children) {
            return new EffectNode(
                    EffectKind.PROJECTILE,
                    primaryElement,
                    new ShapeSpec(boundary, scaleFor(boundary)),
                    TriggerSpec.ON_ACTIVATE,
                    DurationSpec.INSTANT,
                    attributes,
                    children);
        }
    }

    private static float scaleFor(com.astune.gyromancy.api.symbol.PositionedGlyph circle) {
        double area = Math.max(0.0, circle.length() * circle.width());
        return (float)Math.max(0.1F, Math.sqrt(area) * 0.5);
    }
}
