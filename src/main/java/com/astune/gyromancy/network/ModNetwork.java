package com.astune.gyromancy.network;

import com.astune.gyromancy.Gyromancy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = Gyromancy.MODID)
public final class ModNetwork {

    private ModNetwork() {}

    @SubscribeEvent
    static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(Gyromancy.MODID).versioned("1");

        registrar.playToClient(
                SyncDebugElementPacket.TYPE,
                SyncDebugElementPacket.STREAM_CODEC,
                SyncDebugElementPacket::handleClient
        );

        registrar.playToClient(
                SyncGlyphPacket.TYPE,
                SyncGlyphPacket.STREAM_CODEC,
                SyncGlyphPacket::handleClient
        );

        registrar.playToClient(
                SyncArrayPacket.TYPE,
                SyncArrayPacket.STREAM_CODEC,
                SyncArrayPacket::handleClient
        );

        registrar.playToClient(
                CanvasSnapshotPacket.TYPE,
                CanvasSnapshotPacket.STREAM_CODEC,
                CanvasSnapshotPacket::handleClient
        );

        registrar.playToServer(
                SubmitCanvasEditPacket.TYPE,
                SubmitCanvasEditPacket.STREAM_CODEC,
                SubmitCanvasEditPacket::handleServer
        );

        registrar.playToServer(
                SubmitCanvasInventoryPacket.TYPE,
                SubmitCanvasInventoryPacket.STREAM_CODEC,
                SubmitCanvasInventoryPacket::handleServer
        );

        registrar.playToClient(
                StampEditorSnapshotPacket.TYPE,
                StampEditorSnapshotPacket.STREAM_CODEC,
                StampEditorSnapshotPacket::handleClient
        );

        registrar.playToServer(
                SubmitStampCarvingPacket.TYPE,
                SubmitStampCarvingPacket.STREAM_CODEC,
                SubmitStampCarvingPacket::handleServer
        );
    }
}
