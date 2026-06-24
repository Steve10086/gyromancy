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

/**
 * Unified symbol template registry.
 *
 * <p>All templates (center symbols, outer circles, parameter runes) are defined
 * in a single list — the sole configuration point. Each entry has a name (must
 * match a PNG in {@code /assets/gyromancy/textures/symbol/}), feature points,
 * rotation flag, and {@link SymbolRole}.
 */
@EventBusSubscriber(modid = Gyromancy.MODID)
public final class SymbolRegistry {

    private static final String DIR = "/assets/gyromancy/textures/symbol/";

    // ═══════════════════ Configuration point — add new symbols here ═══════════════════

    record SymbolDef(String name, int featurePoints, boolean allowRotation, SymbolRole role) {}

    private static final SymbolDef[] SYMBOLS = {
            new SymbolDef("arrow",          0, false, SymbolRole.CENTER_SYMBOL),
            new SymbolDef("circle_outer",   0, false, SymbolRole.OUTER_CIRCLE),
            new SymbolDef("earth",          4, false, SymbolRole.CENTER_SYMBOL),
            new SymbolDef("figure_8",       0, false, SymbolRole.CENTER_SYMBOL),
            new SymbolDef("fire",           3, false, SymbolRole.CENTER_SYMBOL),
            new SymbolDef("revert",         0, false, SymbolRole.CENTER_SYMBOL),
            new SymbolDef("star",           5, true,  SymbolRole.CENTER_SYMBOL),
            new SymbolDef("water",          0, false, SymbolRole.CENTER_SYMBOL),
            new SymbolDef("wind",           0, true,  SymbolRole.CENTER_SYMBOL),
    };

    // ═══════════════════ Registration ═══════════════════

    private SymbolRegistry() {}

    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(GyromancyRegistries.SYMBOL_KEY, registry -> {
            int loaded = 0;
            for (SymbolDef def : SYMBOLS) {
                int[][] pattern = TemplateLoader.load(DIR + def.name + ".png");
                if (isEmptyPattern(pattern)) {
                    Gyromancy.LOGGER.warn("[SymbolRegistry] Skipping {} — empty or missing PNG", def.name);
                    continue;
                }
                SymbolTemplate t = new SymbolTemplate(
                        rl(def.name), pattern, def.featurePoints,
                        def.allowRotation, false, def.role);
                registry.register(t.id(), t);
                loaded++;
            }
            Gyromancy.LOGGER.info("[SymbolRegistry] Loaded {} symbol templates", loaded);
            SkeletonMatcher.getInstance().init();
        });
    }

    static {
        // Pre-register with SkeletonMatcher (classpath-based, before NeoForge events)
        for (SymbolDef def : SYMBOLS)
            SkeletonMatcher.getInstance().registerTemplate(
                    ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, def.name),
                    DIR + def.name + ".png");
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
