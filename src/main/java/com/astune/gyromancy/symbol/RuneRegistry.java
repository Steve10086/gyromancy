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
public final class RuneRegistry {

    private static final String DIR = "/assets/gyromancy/textures/rune/";

    private RuneRegistry() {}

    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(GyromancyRegistries.RUNE_KEY, registry -> {
            register(registry, "magnitude_1", 1);
            register(registry, "magnitude_2", 2);
            register(registry, "magnitude_3", 3);
            register(registry, "magnitude_4", 4);
            register(registry, "magnitude_5", 5);
            register(registry, "rune_fire",   3);
            register(registry, "rune_water",  0);
            register(registry, "rune_earth",  4);
            register(registry, "rune_wind",   0, true);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<SymbolTemplate> registry,
                                 String name, int featurePoints) {
        register(registry, name, featurePoints, false);
    }

    private static void register(RegisterEvent.RegisterHelper<SymbolTemplate> registry,
                                 String name, int featurePoints, boolean allowRotation) {
        int[][] pattern = TemplateLoader.load(DIR + name + ".png");
        SymbolTemplate t = new SymbolTemplate(rl(name), pattern, featurePoints,
                allowRotation, false, SymbolRole.PARAMETER_RUNE);
        registry.register(t.id(), t);
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, path);
    }
}
