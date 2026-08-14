package com.astune.gyromancy.client.canvas;

import com.astune.gyromancy.api.geometry.SurfaceFrame;
import com.astune.gyromancy.entity.projection.ProjectionCanvasEntity;
import com.astune.gyromancy.entity.projection.WandProjectionEntity;
import com.astune.gyromancy.entity.projection.WandProjectionPose;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Provides a frame-rate pose for the local player's wand projection. */
public final class ProjectionCanvasRenderPose {
    private ProjectionCanvasRenderPose() {}

    public static SurfaceFrame frame(ProjectionCanvasEntity projection, float partialTick) {
        if (!(projection instanceof WandProjectionEntity wandProjection)) {
            return projection.renderSurfaceFrame(partialTick);
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || wandProjection.owner() == null
                || !wandProjection.owner().equals(player.getUUID())) {
            return projection.renderSurfaceFrame(partialTick);
        }

        Vec3 view = player.getViewVector(partialTick).normalize();
        Vec3 center = WandProjectionPose.targetCenter(
                player.getEyePosition(partialTick), view,
                wandProjection.projectionOffset(), player.getViewYRot(partialTick),
                wandProjection.mirrorsWandOffset());
        return projection.renderSurfaceFrameAt(partialTick, center, view);
    }
}
