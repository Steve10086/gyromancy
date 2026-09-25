package com.astune.gyromancy.ink;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.ink.InkType;
import com.astune.gyromancy.registry.GyromancyRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

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

    private static final InkDef[] INKS = {
            new InkDef("mana_ink", 0xFFFFFFFF, MANA_INK_ID),
    };

    record InkDef(String name, int color, int manaValue) {}

    // ═══════════════════ Registration ═══════════════════

    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(GyromancyRegistries.INK_KEY, registry -> {
            for (InkDef def : INKS) {
                ResourceLocation id = rl(def.name);
                InkType ink = InkType.builder(id)
                        .color(def.color)
                        .manaValue(def.manaValue)
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
