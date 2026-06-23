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
import java.util.Map;
import java.util.Set;

/**
 * Auto-discovers 32×32 PNG rune templates from
 * {@code /assets/gyromancy/textures/rune/}.
 */
@EventBusSubscriber(modid = Gyromancy.MODID)
public final class RuneRegistry {

    private static final String DIR = "/assets/gyromancy/textures/rune/";

    // ── Register new runes by adding their PNG filename (without .png) here ──
    private static final String[] RUNES = {
            "magnitude_1",
            "magnitude_2",
            "magnitude_3",
            "magnitude_4",
            "magnitude_5",
            "rune_fire",
            "rune_water",
            "rune_earth",
            "rune_wind",
    };

    private static final Map<String, Integer> FEATURE_POINTS = Map.of(
            "magnitude_1", 1,
            "magnitude_2", 2,
            "magnitude_3", 3,
            "magnitude_4", 4,
            "magnitude_5", 5,
            "rune_fire", 3,
            "rune_earth", 4
    );

    private static final Set<String> ALLOW_ROTATION = Set.of("rune_wind");

    private RuneRegistry() {}

    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(GyromancyRegistries.RUNE_KEY, registry -> {
            int loaded = 0;
            for (String name : RUNES) {
                int[][] pattern = TemplateLoader.load(DIR + name + ".png");
                if (isEmpty(pattern)) {
                    Gyromancy.LOGGER.warn("[RuneRegistry] Skipping {} — empty or missing PNG", name);
                    continue;
                }
                int fp = FEATURE_POINTS.getOrDefault(name, 0);
                boolean rot = ALLOW_ROTATION.contains(name);
                SymbolTemplate t = new SymbolTemplate(rl(name), pattern, fp, rot, false, SymbolRole.PARAMETER_RUNE);
                registry.register(t.id(), t);
                loaded++;
            }
            Gyromancy.LOGGER.info("[RuneRegistry] Loaded {} rune templates", loaded);
        });
    }

    private static boolean isEmpty(int[][] p) {
        if (p.length == 0 || p[0].length == 0) return true;
        for (int[] row : p) for (int v : row) if (v != 0) return false;
        return true;
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, path);
    }
}
