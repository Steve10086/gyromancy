package com.astune.gyromancy.symbol;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.ParameterRune;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.api.symbol.SymbolTemplate;
import com.astune.gyromancy.registry.GyromancyRegistries;
import com.astune.gyromancy.util.TemplateLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;
import java.util.Map;

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
    public static final int DEFAULT_GLYPH_COLOR = 0xFFFFFFFF;
    public static final int FIRE_GLYPH_COLOR = 0xFFFF8888;
    public static final int WATER_GLYPH_COLOR = 0xFF8888FF;
    public static final int EARTH_GLYPH_COLOR = 0xFF8B4513;
    public static final int WIND_GLYPH_COLOR = 0xFF88FFFF;
    public static final int STAR_GLYPH_COLOR = 0xFFFFFF88;

    // ═══════════════════ Configuration point — add new symbols here ═══════════════════

    private static final SkeletonMatcher.SoftThresholds ARROW_THRESHOLDS =
            new SkeletonMatcher.SoftThresholds(0.70, 0.75, 0.70, 0.70, 0.95);
    private static final SkeletonMatcher.SoftThresholds CIRCLE_OUTER_THRESHOLDS =
            new SkeletonMatcher.SoftThresholds(0.95, 0.95, 0.95, 0.95, 0.95);

    /** Behavior executed when this center symbol forms a valid array with runes.
     *  @return scratch data passed to {@link EndEffect} when the array is destroyed. */
    @FunctionalInterface
    public interface CenterEffect {
        Map<String, Object> execute(ServerLevel level, BlockPos arrayPos, List<ParameterRune> runes);
    }

    /** Behavior executed when an array bound to this center symbol is destroyed. */
    @FunctionalInterface
    public interface EndEffect {
        void execute(ServerLevel level, BlockPos arrayPos, List<ParameterRune> runes,
                     Map<String, Object> scratchData);
    }

    private static final CenterEffect NOOP_CENTER = (level, pos, runes) -> Map.of();
    private static final EndEffect NOOP_END = (level, pos, runes, data) -> {};

    record SymbolDef(String name, int featurePoints, boolean allowRotation,
                     SymbolRole role, SkeletonMatcher.SoftThresholds thresholds,
                     int glyphColor, CenterEffect centerEffect, EndEffect endEffect) {
        SymbolDef(String name, int featurePoints, boolean allowRotation, SymbolRole role) {
            this(name, featurePoints, allowRotation, role,
                    SkeletonMatcher.DEFAULT_THRESHOLDS, DEFAULT_GLYPH_COLOR,
                    NOOP_CENTER, NOOP_END);
        }

        SymbolDef(String name, int featurePoints, boolean allowRotation,
                  SymbolRole role, int glyphColor) {
            this(name, featurePoints, allowRotation, role,
                    SkeletonMatcher.DEFAULT_THRESHOLDS, glyphColor,
                    NOOP_CENTER, NOOP_END);
        }

        SymbolDef(String name, int featurePoints, boolean allowRotation,
                  SymbolRole role, SkeletonMatcher.SoftThresholds thresholds) {
            this(name, featurePoints, allowRotation, role,
                    thresholds, DEFAULT_GLYPH_COLOR,
                    NOOP_CENTER, NOOP_END);
        }
    }

    private static final SymbolDef[] SYMBOLS = {
            new SymbolDef("arrow",          0, false, SymbolRole.PARAMETER_RUNE, ARROW_THRESHOLDS),
            new SymbolDef("circle_outer",   0, false, SymbolRole.OUTER_CIRCLE, CIRCLE_OUTER_THRESHOLDS),
            new SymbolDef("earth",          4, false, SymbolRole.CENTER_SYMBOL, EARTH_GLYPH_COLOR),
            new SymbolDef("figure_8",       0, false, SymbolRole.PARAMETER_RUNE),
            new SymbolDef("fire",           3, false, SymbolRole.CENTER_SYMBOL, FIRE_GLYPH_COLOR),
            new SymbolDef("revert",         0, false, SymbolRole.PARAMETER_RUNE),
            new SymbolDef("star",           5, true,  SymbolRole.CENTER_SYMBOL, STAR_GLYPH_COLOR),
            new SymbolDef("water",          0, false, SymbolRole.CENTER_SYMBOL, WATER_GLYPH_COLOR),
            new SymbolDef("wind",           0, true,  SymbolRole.CENTER_SYMBOL, WIND_GLYPH_COLOR),
    };

    // ═══════════════════ Registration ═══════════════════

    static SkeletonMatcher.SoftThresholds thresholdsFor(String name) {
        for (SymbolDef def : SYMBOLS)
            if (def.name.equals(name)) return def.thresholds;
        return SkeletonMatcher.DEFAULT_THRESHOLDS;
    }

    public static int symbolLayerValueFor(ResourceLocation id) {
        SymbolTemplate template = GyromancyRegistries.SYMBOL.get(id);
        if (template == null) return 0;
        int registryId = GyromancyRegistries.SYMBOL.getId(template);
        return registryId < 0 ? 0 : registryId + 1;
    }

    /** Returns the center behavior for {@code symbolId}, or a no-op if none is defined. */
    public static CenterEffect getCenterEffect(ResourceLocation symbolId) {
        for (SymbolDef def : SYMBOLS) {
            if (def.name.equals(symbolId.getPath())) return def.centerEffect;
        }
        return NOOP_CENTER;
    }

    /** Returns the end behavior for {@code symbolId}, or a no-op if none is defined. */
    public static EndEffect getEndEffect(ResourceLocation symbolId) {
        for (SymbolDef def : SYMBOLS) {
            if (def.name.equals(symbolId.getPath())) return def.endEffect;
        }
        return NOOP_END;
    }

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
                        def.allowRotation, false, def.role, def.glyphColor);
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
                    DIR + def.name + ".png",
                    def.thresholds);
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
