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
 * Loads effect-defining symbol templates from datapacks and provides hardcoded defaults.
 *
 * <p>Symbols define the FUNCTION of a magic array (e.g., fire, water, heal).
 * Templates are loaded from {@code data/<namespace>/gyromancy/symbol/*.json}
 * and registered into {@link GyromancyRegistries#SYMBOL}.
 *
 * <p>Registration order: hardcoded defaults first, then datapack entries.
 * Datapack entries with the same ID override defaults.
 */
@EventBusSubscriber(modid = Gyromancy.MODID)
public final class SymbolRegistry {

    private static final String DATAPACK_PATH = "gyromancy/symbol";
    private static boolean loaded = false;

    private SymbolRegistry() {}

    @SubscribeEvent
    static void onServerStarting(ServerStartingEvent event) {
        if (loaded) return;
        loaded = true;

        registerDefaults();
        loadFromDatapacks(event.getServer().getResourceManager());

        Gyromancy.LOGGER.info("[SymbolRegistry] Loaded {} symbol templates",
                GyromancyRegistries.SYMBOL.keySet().size());
    }

    // ═══════════════════════════════════════════════════════════════
    // Default hardcoded symbols
    // ═══════════════════════════════════════════════════════════════

    private static void registerDefaults() {
        register(circleOuterTemplate());
        register(fireSymbolTemplate());
        register(waterSymbolTemplate());
        register(earthSymbolTemplate());
        register(windSymbolTemplate());
    }

    /** A ring shape — the outer boundary circle of a magic array */
    private static SymbolTemplate circleOuterTemplate() {
        int[][] pattern = new int[32][32];
        int cx = 15, cy = 15;
        int outerR = 14, innerR = 10;

        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                double dist = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
                if (dist >= innerR && dist <= outerR) {
                    pattern[y][x] = 1;
                }
            }
        }

        return new SymbolTemplate(
                rl("circle_outer"), pattern, 0,
                false, false, SymbolRole.OUTER_CIRCLE
        );
    }

    /** A triangle pointing up — fire symbol */
    private static SymbolTemplate fireSymbolTemplate() {
        int[][] pattern = new int[32][32];
        int cx = 16, topY = 3, baseY = 28;

        for (int y = 0; y < 32; y++) {
            if (y < topY || y > baseY) continue;
            double halfWidth = (baseY - y) * 14.0 / (baseY - topY);
            for (int x = 0; x < 32; x++) {
                if (Math.abs(x - cx) <= halfWidth + 1) {
                    pattern[y][x] = 1;
                }
            }
        }

        return new SymbolTemplate(
                rl("fire_symbol"), pattern, 3,
                false, false, SymbolRole.CENTER_SYMBOL
        );
    }

    /** A wave/drop shape — water symbol */
    private static SymbolTemplate waterSymbolTemplate() {
        int[][] pattern = new int[32][32];

        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                double dx = x - 16.0;
                double dy = y - 16.0;
                // Teardrop shape
                double r = 11;
                if (dy < -r * 0.5) {
                    // Top is rounded
                    if (dx * dx + (dy + r * 0.3) * (dy + r * 0.3) <= r * r) {
                        pattern[y][x] = 1;
                    }
                } else {
                    // Bottom tapers to a point
                    double taper = (r - dy * 0.8) / r * r;
                    if (Math.abs(dx) <= taper * 0.5) {
                        pattern[y][x] = 1;
                    }
                }
            }
        }

        return new SymbolTemplate(
                rl("water_symbol"), pattern, 0,
                false, false, SymbolRole.CENTER_SYMBOL
        );
    }

    /** A square — earth symbol */
    private static SymbolTemplate earthSymbolTemplate() {
        int[][] pattern = new int[32][32];

        for (int y = 5; y <= 26; y++) {
            for (int x = 5; x <= 26; x++) {
                // Solid square with slightly rounded corners
                boolean isCorner = (x <= 6 || x >= 25) && (y <= 6 || y >= 25);
                if (!isCorner || (Math.abs(x - 16) + Math.abs(y - 16) <= 22)) {
                    pattern[y][x] = 1;
                }
            }
        }

        return new SymbolTemplate(
                rl("earth_symbol"), pattern, 4,
                false, false, SymbolRole.CENTER_SYMBOL
        );
    }

    /** A swirl/spiral — wind symbol */
    private static SymbolTemplate windSymbolTemplate() {
        int[][] pattern = new int[32][32];

        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                double dx = x - 15.5;
                double dy = y - 15.5;
                double angle = Math.atan2(dy, dx);
                double dist = Math.sqrt(dx * dx + dy * dy);

                // Three-armed spiral
                double target = dist / 13.0 * Math.PI * 2 + angle;
                if (Math.abs(Math.sin(target * 3)) < 0.4 && dist < 14) {
                    pattern[y][x] = 1;
                }
            }
        }

        return new SymbolTemplate(
                rl("wind_symbol"), pattern, 0,
                true, false, SymbolRole.CENTER_SYMBOL
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // Datapack loading
    // ═══════════════════════════════════════════════════════════════

    private static void loadFromDatapacks(ResourceManager resourceManager) {
        try {
            // Scan all namespaces for gyromancy/symbol/*.json
            var resources = resourceManager.listResources(
                    DATAPACK_PATH,
                    path -> path.getPath().endsWith(".json")
            );

            for (var entry : resources.entrySet()) {
                ResourceLocation filePath = entry.getKey();
                try {
                    loadSymbolFromResource(filePath, entry.getValue());
                } catch (Exception e) {
                    Gyromancy.LOGGER.error("[SymbolRegistry] Failed to load symbol from {}", filePath, e);
                }
            }
        } catch (Exception e) {
            Gyromancy.LOGGER.warn("[SymbolRegistry] Failed to scan datapack symbols: {}", e.getMessage());
        }
    }

    private static void loadSymbolFromResource(ResourceLocation filePath, Resource resource) {
        // Derive symbol ID from file path: "gyromancy/symbol/<name>.json" → "namespace:<name>"
        String path = filePath.getPath();
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        String symbolName = fileName.substring(0, fileName.lastIndexOf('.'));
        ResourceLocation symbolId = ResourceLocation.fromNamespaceAndPath(
                filePath.getNamespace(), symbolName);

        try (InputStreamReader reader = new InputStreamReader(resource.open())) {
            var json = com.google.gson.JsonParser.parseReader(reader);
            var result = SymbolTemplate.CODEC.parse(JsonOps.INSTANCE, json);

            Optional<SymbolTemplate> parsed = result.resultOrPartial(
                    error -> Gyromancy.LOGGER.error("[SymbolRegistry] Error parsing {}: {}", symbolId, error)
            );

            parsed.ifPresent(template -> {
                // Use the file-path-derived ID, not the JSON "id" field
                SymbolTemplate corrected = new SymbolTemplate(
                        symbolId,
                        template.pattern(),
                        template.featurePoints(),
                        template.allowRotation(),
                        template.allowMirror(),
                        template.defaultRole()
                );
                register(corrected);
            });
        } catch (Exception e) {
            Gyromancy.LOGGER.error("[SymbolRegistry] Error reading symbol {}: {}", symbolId, e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Registration
    // ═══════════════════════════════════════════════════════════════

    /** Registers a single symbol template into the custom registry */
    public static void register(SymbolTemplate template) {
        Registry.register(GyromancyRegistries.SYMBOL, template.id(), template);
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, path);
    }
}
