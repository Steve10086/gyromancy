package com.astune.gyromancy.client.canvas;

import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.canvas.CanvasEntity;
import com.astune.gyromancy.canvas.CanvasToolSettings;
import com.astune.gyromancy.entity.projection.ProjectionCanvasEntity;
import com.astune.gyromancy.network.CanvasSnapshotPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Client-only cache for entity rasters, which are too large for synched data. */
public final class CanvasClientState {
    private static final Map<Integer, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private CanvasClientState() {}

    public static void accept(CanvasSnapshotPacket packet) {
        SNAPSHOTS.compute(packet.entityId(), (entityId, previous) -> {
            if (previous != null
                    && previous.revision == packet.revision()
                    && previous.document.equals(packet.document())) {
                return previous;
            }
            if (previous != null) previous.close();
            return new Snapshot(entityId, packet.revision(), packet.document());
        });
        if (packet.openEditor()) {
            Minecraft.getInstance().setScreen(
                    new CanvasEditorScreen(packet.entityId(), packet.revision(), packet.document()));
        }
    }

    public static CanvasDocument document(int entityId) {
        Snapshot snapshot = SNAPSHOTS.get(entityId);
        return snapshot == null ? null : snapshot.document();
    }

    public static ResourceLocation textureLocation(CanvasEntity entity) {
        Snapshot snapshot = SNAPSHOTS.get(entity.getId());
        return snapshot == null ? null : snapshot.textureLocation(
                entity instanceof ProjectionCanvasEntity);
    }

    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()
                && event.getEntity() instanceof CanvasEntity canvas) {
            remove(canvas.getId());
        }
    }

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
        CanvasTooltipTextureCache.clear();
        CanvasToolSettings.clear();
    }

    private static void remove(int entityId) {
        Snapshot snapshot = SNAPSHOTS.remove(entityId);
        if (snapshot != null) snapshot.close();
    }

    private static void clear() {
        SNAPSHOTS.values().forEach(Snapshot::close);
        SNAPSHOTS.clear();
    }

    private static final class Snapshot implements AutoCloseable {
        private final int entityId;
        private final int revision;
        private final CanvasDocument document;
        private CanvasDynamicTexture texture;

        private Snapshot(int entityId, int revision, CanvasDocument document) {
            this.entityId = entityId;
            this.revision = revision;
            this.document = document;
        }

        private ResourceLocation textureLocation(boolean transparent) {
            if (texture == null) {
                texture = transparent
                        ? CanvasDynamicTexture.createOverlay(
                                "entity/" + entityId,
                                document.resolutionWidth(),
                                document.resolutionHeight(),
                                document.colors(),
                                true)
                        : CanvasDynamicTexture.create(
                                "entity/" + entityId,
                                document,
                                true);
            }
            return texture.location();
        }

        private CanvasDocument document() {
            return document;
        }

        @Override
        public void close() {
            if (texture != null) {
                texture.close();
                texture = null;
            }
        }
    }
}
