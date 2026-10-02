package com.astune.gyromancy.client.effect;

import com.astune.gyromancy.api.element.ElementType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Client-side registry and ticker for position-anchored instant spell FX. */
@OnlyIn(Dist.CLIENT)
public final class InstantSpellEffects {
    private static final List<InstantSpellEffect> ACTIVE = new ArrayList<>();

    private InstantSpellEffects() {}

    /** Handles a server notice: starts the tinted effect at the given position. */
    public static void onFx(Vec3 position, float radius, int elementIndex) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        ElementType element = ElementType.byIndex(elementIndex);
        InstantSpellEffect effect =
                new InstantSpellEffect(level, position, radius * 2.0F, element.color());
        effect.start();
        if (effect.isAlive()) ACTIVE.add(effect);
    }

    /** Ticks and reaps active effects once per rendered frame at particle stage. */
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (ACTIVE.isEmpty()) return;
        if (Minecraft.getInstance().level == null) {
            clear();
            return;
        }

        Iterator<InstantSpellEffect> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            InstantSpellEffect effect = iterator.next();
            effect.tick();
            if (!effect.isAlive()) iterator.remove();
        }
    }

    /** Drops every tracked effect when the client level goes away. */
    public static void clear() {
        for (InstantSpellEffect effect : ACTIVE) effect.stop();
        ACTIVE.clear();
    }
}
