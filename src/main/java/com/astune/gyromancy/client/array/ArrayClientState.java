package com.astune.gyromancy.client.array;

import com.astune.gyromancy.client.effect.ClientRayEffects;
import com.astune.gyromancy.network.SyncArrayPacket;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Clears dimension/session-local array visuals when the client disconnects. */
public final class ArrayClientState {
    private ArrayClientState() {}

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        SyncArrayPacket.resetClientState();
        ClientRayEffects.clearAll();
    }

    /** Clears server-synchronised array identities when a client level unloads. */
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        SyncArrayPacket.resetClientState();
        ClientRayEffects.clearAll();
    }
}
