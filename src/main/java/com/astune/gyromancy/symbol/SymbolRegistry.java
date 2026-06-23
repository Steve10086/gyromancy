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
 * Auto-discovers 32×32 PNG symbol templates from
 * {@code /assets/gyromancy/textures/symbol/}.
 * Black pixels = foreground (1), non-black = background (0).
 */
@EventBusSubscriber(modid = Gyromancy.MODID)
public final class SymbolRegistry {

    private static final String DIR = "/assets/gyromancy/textures/symbol/";

    // ── Register new symbols by adding their PNG filename (without .png) here ──
    private static final String[] SYMBOLS = {
            "circle_outer",
            "square",
            "star",
            "triangle",
            "figure_8",
            "fire_symbol",
            "water_symbol",
            "earth_symbol",
            "arrow",
            "revert",
            "wind",
    };

    // Feature points for specific symbols (default: 0)
    private static final Map<String, Integer> FEATURE_POINTS = Map.ofEntries(
            Map.entry("square", 4),
            Map.entry("star", 5),
            Map.entry("triangle", 3),
            Map.entry("fire_symbol", 3),
            Map.entry("earth_symbol", 4)
    );

    // Symbols that allow rotation (default: false)
    private static final Set<String> ALLOW_ROTATION = Set.of("star", "wind");

    // Default role per symbol (default: CENTER_SYMBOL)
    private static final Map<String, SymbolRole> ROLES = Map.of(
            "circle_outer", SymbolRole.OUTER_CIRCLE
    );

    private SymbolRegistry() {}

    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(GyromancyRegistries.SYMBOL_KEY, registry -> {
            int loaded = 0;
            for (String name : SYMBOLS) {
                int[][] pattern = TemplateLoader.load(DIR + name + ".png");
                if (isEmptyPattern(pattern)) {
                    Gyromancy.LOGGER.warn("[SymbolRegistry] Skipping {} — empty or missing PNG", name);
                    continue;
                }
                int fp = FEATURE_POINTS.getOrDefault(name, 0);
                boolean rot = ALLOW_ROTATION.contains(name);
                SymbolRole role = ROLES.getOrDefault(name, SymbolRole.CENTER_SYMBOL);
                SymbolTemplate t = new SymbolTemplate(rl(name), pattern, fp, rot, false, role);
                registry.register(t.id(), t);
                loaded++;
            }
            Gyromancy.LOGGER.info("[SymbolRegistry] Loaded {} symbol templates", loaded);
        });
    }

    private static boolean isEmptyPattern(int[][] p) {
        if (p.length == 0 || p[0].length == 0) return true;
        for (int[] row : p)
            for (int v : row) if (v != 0) return false;
        return true;
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, path);
    }
}
