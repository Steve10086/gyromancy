package com.astune.gyromancy.registry;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.SymbolTemplate;
import com.astune.gyromancy.symbol.ArrowSymbol;
import com.astune.gyromancy.symbol.CircleOuterSymbol;
import com.astune.gyromancy.symbol.CrossSymbol;
import com.astune.gyromancy.symbol.CurlSymbol;
import com.astune.gyromancy.symbol.DarkSymbol;
import com.astune.gyromancy.symbol.DrainSymbol;
import com.astune.gyromancy.symbol.EarthSymbol;
import com.astune.gyromancy.symbol.EliminateSymbol;
import com.astune.gyromancy.symbol.EngagingSymbol;
import com.astune.gyromancy.symbol.FireSymbol;
import com.astune.gyromancy.symbol.FixSymbol;
import com.astune.gyromancy.symbol.ManaSymbol;
import com.astune.gyromancy.symbol.RevertSymbol;
import com.astune.gyromancy.symbol.SkeletonMatcher;
import com.astune.gyromancy.symbol.SpaceSymbol;
import com.astune.gyromancy.symbol.SplitSymbol;
import com.astune.gyromancy.symbol.StarSymbol;
import com.astune.gyromancy.symbol.Symbol;
import com.astune.gyromancy.symbol.WaterSymbol;
import com.astune.gyromancy.symbol.WindSymbol;
import com.astune.gyromancy.util.TemplateLoader;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;

/** Symbol template registrations for Gyromancy. */
public final class ModSymbols {

    private static final List<Symbol> SYMBOLS = new ArrayList<>();

    static {
        registerSymbol(ArrowSymbol.INSTANCE);
        registerSymbol(CircleOuterSymbol.INSTANCE);
        registerSymbol(CrossSymbol.INSTANCE);
        registerSymbol(CurlSymbol.INSTANCE);
        registerSymbol(DarkSymbol.INSTANCE);
        registerSymbol(DrainSymbol.INSTANCE);
        registerSymbol(EarthSymbol.INSTANCE);
        registerSymbol(EliminateSymbol.INSTANCE);
        registerSymbol(EngagingSymbol.INSTANCE);
        registerSymbol(FireSymbol.INSTANCE);
        registerSymbol(FixSymbol.INSTANCE);
        registerSymbol(ManaSymbol.INSTANCE);
        registerSymbol(RevertSymbol.INSTANCE);
        registerSymbol(SpaceSymbol.INSTANCE);
        registerSymbol(SplitSymbol.INSTANCE);
        registerSymbol(StarSymbol.INSTANCE);
        registerSymbol(WaterSymbol.INSTANCE);
        registerSymbol(WindSymbol.INSTANCE);
    }

    private ModSymbols() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModSymbols::onRegister);
    }

    public static List<Symbol> symbols() {
        return List.copyOf(SYMBOLS);
    }

    private static void registerSymbol(Symbol symbol) {
        SYMBOLS.add(symbol);
        SkeletonMatcher.getInstance().registerTemplate(symbol.id(), symbol.resourcePath(), symbol.thresholds());
    }

    public static int symbolLayerValueFor(ResourceLocation id) {
        SymbolTemplate template = GyromancyRegistries.SYMBOL.get(id);
        if (template == null) return 0;
        int registryId = GyromancyRegistries.SYMBOL.getId(template);
        return registryId < 0 ? 0 : registryId + 1;
    }

    private static void onRegister(RegisterEvent event) {
        event.register(GyromancyRegistries.SYMBOL_KEY, registry -> {
            int loaded = 0;
            for (Symbol sym : SYMBOLS) {
                int[][] pattern = TemplateLoader.load(sym.resourcePath());
                if (isEmptyPattern(pattern)) {
                    Gyromancy.LOGGER.warn("[ModSymbols] Skipping {} - empty or missing PNG", sym.name());
                    continue;
                }
                SymbolTemplate t = new SymbolTemplate(
                        sym.id(), pattern, sym.featurePoints(),
                        sym.allowRotation(), false, sym.role(), sym.glyphColor());
                registry.register(t.id(), t);
                loaded++;
            }
            Gyromancy.LOGGER.info("[ModSymbols] Loaded {} symbol templates", loaded);
            SkeletonMatcher.getInstance().init();
        });
    }

    private static boolean isEmptyPattern(int[][] p) {
        if (p.length == 0 || p[0].length == 0) return true;
        for (int[] row : p)
            for (int v : row) if (v != 0) return false;
        return true;
    }

}
