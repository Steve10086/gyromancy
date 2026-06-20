package com.astune.gyromancy.symbol;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.api.symbol.SymbolTemplate;
import com.astune.gyromancy.registry.GyromancyRegistries;
import com.astune.gyromancy.util.TemplateLoader;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = Gyromancy.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class SymbolRegistry {

    private static final String DIR = "/assets/gyromancy/textures/symbol/";

    private SymbolRegistry() {}

    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(GyromancyRegistries.SYMBOL_KEY, registry -> {
            register(registry, "circle_outer",   SymbolRole.OUTER_CIRCLE,   0);
            register(registry, "square",         SymbolRole.CENTER_SYMBOL,  4);
            register(registry, "triangle",       SymbolRole.CENTER_SYMBOL,  3);
            register(registry, "star",           SymbolRole.CENTER_SYMBOL,  5, true);
            register(registry, "figure_8",       SymbolRole.CENTER_SYMBOL,  0);
            register(registry, "fire_symbol",    SymbolRole.CENTER_SYMBOL,  3);
            register(registry, "water_symbol",   SymbolRole.CENTER_SYMBOL,  0);
            register(registry, "earth_symbol",   SymbolRole.CENTER_SYMBOL,  4);
            register(registry, "wind_symbol",    SymbolRole.CENTER_SYMBOL,  0, true);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<SymbolTemplate> registry,
                                 String name, SymbolRole role, int featurePoints) {
        register(registry, name, role, featurePoints, false);
    }

    private static void register(RegisterEvent.RegisterHelper<SymbolTemplate> registry,
                                 String name, SymbolRole role, int featurePoints, boolean allowRotation) {
        int[][] pattern = TemplateLoader.load(DIR + name + ".png");
        SymbolTemplate t = new SymbolTemplate(rl(name), pattern, featurePoints,
                allowRotation, false, role);
        registry.register(t.id(), t);
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, path);
    }
}
