package com.astune.gyromancy.symbol;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.api.symbol.SymbolTemplate;
import com.astune.gyromancy.registry.GyromancyRegistries;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import java.io.InputStreamReader;
import java.util.Optional;

/**
 * Loads parameter-rune templates from datapacks and provides hardcoded defaults.
 *
 * <p>Runes define PARAMETERS for magic array effects (e.g., magnitude, element type).
 * Templates are loaded from {@code data/<namespace>/gyromancy/rune/*.json}
 * and registered into {@link GyromancyRegistries#RUNE}.
 *
 * <p>Registration order: hardcoded defaults first, then datapack entries.
 */
@EventBusSubscriber(modid = Gyromancy.MODID)
public final class RuneRegistry {

    private static final String DATAPACK_PATH = "gyromancy/rune";
    private static boolean loaded = false;

    private RuneRegistry() {}

    @SubscribeEvent
    static void onServerStarting(ServerStartingEvent event) {
        if (loaded) return;
        loaded = true;

        registerDefaults();
        loadFromDatapacks(event.getServer().getResourceManager());

        Gyromancy.LOGGER.info("[RuneRegistry] Loaded {} rune templates",
                GyromancyRegistries.RUNE.keySet().size());
    }

    // ═══════════════════════════════════════════════════════════════
    // Default hardcoded runes
    // ═══════════════════════════════════════════════════════════════

    private static void registerDefaults() {
        // Magnitude runes: 1–5 horizontal bars
        for (int i = 1; i <= 5; i++) {
            register(magnitudeRune(i));
        }

        // Element selector runes (small glyphs)
        register(elementFireRune());
        register(elementWaterRune());
        register(elementEarthRune());
        register(elementWindRune());
    }

    /** Horizontal bars indicating magnitude (1–5) */
    private static SymbolTemplate magnitudeRune(int count) {
        int[][] pattern = new int[32][32];

        // Each bar is 3 pixels tall with 3 pixels gap, centered vertically
        int totalHeight = count * 6 - 3;
        int startY = (32 - totalHeight) / 2;

        for (int i = 0; i < count; i++) {
            int barY = startY + i * 6;
            for (int y = barY; y < barY + 3 && y < 32; y++) {
                for (int x = 6; x <= 25; x++) {
                    pattern[y][x] = 1;
                }
            }
        }

        return new SymbolTemplate(
                rl("magnitude_" + count), pattern, count,
                false, false, SymbolRole.PARAMETER_RUNE
        );
    }

    /** Small fire rune glyph */
    private static SymbolTemplate elementFireRune() {
        int[][] pattern = new int[32][32];
        int cx = 16, topY = 8, baseY = 24;

        for (int y = 0; y < 32; y++) {
            if (y < topY || y > baseY) continue;
            double halfWidth = (baseY - y) * 8.0 / (baseY - topY);
            for (int x = 0; x < 32; x++) {
                if (Math.abs(x - cx) <= halfWidth + 1) {
                    pattern[y][x] = 1;
                }
            }
        }

        return new SymbolTemplate(
                rl("element_fire"), pattern, 3,
                false, false, SymbolRole.PARAMETER_RUNE
        );
    }

    /** Small water rune glyph */
    private static SymbolTemplate elementWaterRune() {
        int[][] pattern = new int[32][32];

        for (int y = 0; y < 32; y++) {
            double offset = Math.sin(y * 0.8) * 5;
            for (int x = 0; x < 32; x++) {
                if (Math.abs(x - 16 - offset) <= 2) {
                    pattern[y][x] = 1;
                }
            }
        }

        return new SymbolTemplate(
                rl("element_water"), pattern, 0,
                false, false, SymbolRole.PARAMETER_RUNE
        );
    }

    /** Small earth rune glyph */
    private static SymbolTemplate elementEarthRune() {
        int[][] pattern = new int[32][32];

        for (int y = 10; y <= 21; y++) {
            for (int x = 10; x <= 21; x++) {
                pattern[y][x] = 1;
            }
        }

        return new SymbolTemplate(
                rl("element_earth"), pattern, 4,
                false, false, SymbolRole.PARAMETER_RUNE
        );
    }

    /** Small wind rune glyph */
    private static SymbolTemplate elementWindRune() {
        int[][] pattern = new int[32][32];

        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                double dx = x - 15.5;
                double dy = y - 15.5;
                double angle = Math.atan2(dy, dx);
                double dist = Math.sqrt(dx * dx + dy * dy);
                double target = dist / 8.0 * Math.PI + angle;
                if (Math.abs(Math.sin(target * 2)) < 0.5 && dist < 12) {
                    pattern[y][x] = 1;
                }
            }
        }

        return new SymbolTemplate(
                rl("element_wind"), pattern, 0,
                true, false, SymbolRole.PARAMETER_RUNE
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // Datapack loading
    // ═══════════════════════════════════════════════════════════════

    private static void loadFromDatapacks(ResourceManager resourceManager) {
        try {
            var resources = resourceManager.listResources(
                    DATAPACK_PATH,
                    path -> path.getPath().endsWith(".json")
            );

            for (var entry : resources.entrySet()) {
                ResourceLocation filePath = entry.getKey();
                try {
                    loadRuneFromResource(filePath, entry.getValue());
                } catch (Exception e) {
                    Gyromancy.LOGGER.error("[RuneRegistry] Failed to load rune from {}", filePath, e);
                }
            }
        } catch (Exception e) {
            Gyromancy.LOGGER.warn("[RuneRegistry] Failed to scan datapack runes: {}", e.getMessage());
        }
    }

    private static void loadRuneFromResource(ResourceLocation filePath, Resource resource) {
        String path = filePath.getPath();
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        String runeName = fileName.substring(0, fileName.lastIndexOf('.'));
        ResourceLocation runeId = ResourceLocation.fromNamespaceAndPath(
                filePath.getNamespace(), runeName);

        try (InputStreamReader reader = new InputStreamReader(resource.open())) {
            var json = com.google.gson.JsonParser.parseReader(reader);
            var result = SymbolTemplate.CODEC.parse(JsonOps.INSTANCE, json);

            Optional<SymbolTemplate> parsed = result.resultOrPartial(
                    error -> Gyromancy.LOGGER.error("[RuneRegistry] Error parsing {}: {}", runeId, error)
            );

            parsed.ifPresent(template -> {
                SymbolTemplate corrected = new SymbolTemplate(
                        runeId,
                        template.pattern(),
                        template.featurePoints(),
                        template.allowRotation(),
                        template.allowMirror(),
                        template.defaultRole()
                );
                register(corrected);
            });
        } catch (Exception e) {
            Gyromancy.LOGGER.error("[RuneRegistry] Error reading rune {}: {}", runeId, e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Registration
    // ═══════════════════════════════════════════════════════════════

    public static void register(SymbolTemplate template) {
        Registry.register(GyromancyRegistries.RUNE, template.id(), template);
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, path);
    }
}
