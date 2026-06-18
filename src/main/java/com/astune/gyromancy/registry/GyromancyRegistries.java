package com.astune.gyromancy.registry;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.array.IArrayEffect;
import com.astune.gyromancy.api.symbol.SymbolTemplate;
import com.astune.gyromancy.api.ink.InkType;
import com.astune.gyromancy.api.symbol.ParameterRune;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

/**
 * Custom registry definitions for Gyromancy.
 * These registries allow data-driven extensibility for symbols, effects, inks, and runes.
 */
@EventBusSubscriber(modid = Gyromancy.MODID)
public final class GyromancyRegistries {

    private GyromancyRegistries() {}

    // ── Registry Keys ──

    public static final ResourceKey<Registry<IArrayEffect>> ARRAY_EFFECT_KEY =
            ResourceKey.createRegistryKey(rl("array_effect"));

    public static final ResourceKey<Registry<SymbolTemplate>> SYMBOL_KEY =
            ResourceKey.createRegistryKey(rl("symbol"));

    public static final ResourceKey<Registry<InkType>> INK_KEY =
            ResourceKey.createRegistryKey(rl("ink"));

    public static final ResourceKey<Registry<SymbolTemplate>> RUNE_KEY =
            ResourceKey.createRegistryKey(rl("rune"));

    // ── Registry instances (populated during NewRegistryEvent) ──

    public static final Registry<IArrayEffect> ARRAY_EFFECT =
            new RegistryBuilder<>(ARRAY_EFFECT_KEY).sync(true).create();

    public static final Registry<SymbolTemplate> SYMBOL =
            new RegistryBuilder<>(SYMBOL_KEY).sync(true).create();

    public static final Registry<InkType> INK =
            new RegistryBuilder<>(INK_KEY).sync(true).create();

    public static final Registry<SymbolTemplate> RUNE =
            new RegistryBuilder<>(RUNE_KEY).sync(true).create();

    @SubscribeEvent
    static void registerRegistries(NewRegistryEvent event) {
        event.register(ARRAY_EFFECT);
        event.register(SYMBOL);
        event.register(INK);
        event.register(RUNE);
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, path);
    }
}
