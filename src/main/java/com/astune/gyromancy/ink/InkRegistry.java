package com.astune.gyromancy.ink;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.ink.InkType;
import com.astune.gyromancy.registry.GyromancyRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Arrays;
import java.util.List;

/**
 * Registers default ink types into the {@code gyromancy:ink} registry.
 *
 * <p>To add a new ink, add a static InkDef entry and register it in
 * {@link #onRegister}. No other code changes needed — PenItem reads
 * whichever ink is in the offhand via the INK_TYPE data component.
 */
@EventBusSubscriber(modid = Gyromancy.MODID)
public final class InkRegistry {

    private InkRegistry() {}

    // ═══════════════════ Configuration point — add new inks here ═══════════════════

    /** Mana id written by the default mana ink; also the wand projection id. */
    public static final int MANA_INK_ID = 20;
    public static final int WATER_INK_ID = 21;
    public static final int FIRE_INK_ID = 22;
    public static final int EARTH_INK_ID = 23;
    public static final int WIND_INK_ID = 24;
    public static final int WATER_MANA_INK_ID = 25;
    public static final int FIRE_MANA_INK_ID = 26;
    public static final int EARTH_MANA_INK_ID = 27;
    public static final int WIND_MANA_INK_ID = 28;
    public static final int LIGHT_MANA_INK_ID = 29;
    public static final int DARK_MANA_INK_ID = 30;

    /** Registry id of the default mana ink, shared with the creative tab. */
    public static final ResourceLocation MANA_INK =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "mana_ink");

    /**
     * Registration order defines the ink registry ids and therefore the
     * {@code gyromancy:ink} model override values (registry id + 1).
     */
    private static final InkDef[] INKS = {
            new InkDef("mana_ink", 0xFFFFFFFF, MANA_INK_ID, 0xFFFFFFFF),
            new InkDef("water_ink", 0xFF7FC8F8, WATER_INK_ID, 0xFF1E4FBF),
            new InkDef("fire_ink", 0xFFFF8A80, FIRE_INK_ID, 0xFFFF0000),
            new InkDef("earth_ink", 0xFFCFA47A, EARTH_INK_ID, 0xFF8B5A2B),
            new InkDef("wind_ink", 0xFFB7E8A0, WIND_INK_ID, 0xFF90EE90),
            new InkDef("water_mana_ink", 0xFF2F6FA8, WATER_MANA_INK_ID, 0xFF1E4FBF),
            new InkDef("fire_mana_ink", 0xFFC43A2A, FIRE_MANA_INK_ID, 0xFFFF0000),
            new InkDef("earth_mana_ink", 0xFF8A6238, EARTH_MANA_INK_ID, 0xFF8B5A2B),
            new InkDef("wind_mana_ink", 0xFF5F9E58, WIND_MANA_INK_ID, 0xFF90EE90),
            new InkDef("light_mana_ink", 0xFFD9B84A, LIGHT_MANA_INK_ID, 0xFFFFE080),
            new InkDef("dark_mana_ink", 0xFF5B3FA8, DARK_MANA_INK_ID, 0xFF4B2E83),
    };

    /** Every registered ink id in registration order, for creative previews. */
    public static final List<ResourceLocation> INK_IDS =
            Arrays.stream(INKS).map(def -> rl(def.name)).toList();

    record InkDef(String name, int color, int manaValue, int overlayTint) {}

    // ═══════════════════ Registration ═══════════════════

    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(GyromancyRegistries.INK_KEY, registry -> {
            for (InkDef def : INKS) {
                ResourceLocation id = rl(def.name);
                InkType ink = InkType.builder(id)
                        .color(def.color)
                        .manaValue(def.manaValue)
                        .overlayTint(def.overlayTint)
                        .build();
                registry.register(id, ink);
            }
            Gyromancy.LOGGER.info("[InkRegistry] Registered {} ink types", INKS.length);
        });
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, path);
    }
}
