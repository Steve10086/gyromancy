package com.astune.gyromancy.canvas;

import com.astune.gyromancy.Gyromancy;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Sends the large raster snapshot only when a player starts tracking a canvas. */
@EventBusSubscriber(modid = Gyromancy.MODID)
public final class CanvasTrackingEvents {
    private CanvasTrackingEvents() {}

    @SubscribeEvent
    static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getTarget() instanceof CanvasEntity canvas) {
            canvas.sendSnapshot(player, false);
        }
    }
}
