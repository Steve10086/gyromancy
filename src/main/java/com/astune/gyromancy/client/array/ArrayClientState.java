package com.astune.gyromancy.client.array;

import com.astune.gyromancy.client.effect.ClientRayEffects;
import com.astune.gyromancy.network.SyncArrayPacket;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Clears dimension/session-local array visuals when the client disconnects. */
public final class ArrayClientState {
    private ArrayClientState() {}

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        SyncArrayPacket.resetClientState();
        ClientRayEffects.clearAll();
    }
}
