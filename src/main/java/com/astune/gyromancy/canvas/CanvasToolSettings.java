package com.astune.gyromancy.canvas;

import com.astune.gyromancy.network.UpdateCanvasToolSettingsPacket;
import com.astune.gyromancy.registry.ModAttachments;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Per-character editor settings shared by every canvas-based screen.
 *
 * <p>These values describe the local editing tool, not the item stack. They
 * are stored as a synced player attachment, so they survive logout, death and
 * dimension changes; the server keeps the authoritative copy while a client
 * update is mirrored locally for immediate feedback.
 */
public final class CanvasToolSettings {
    public static final int DEFAULT_PEN_DIAMETER = 1;
    public static final int MIN_PEN_DIAMETER = 1;
    public static final int MAX_PEN_DIAMETER = 31;
    public static final double DEFAULT_STAMP_SCALE = 1.0;
    public static final double MIN_STAMP_SCALE = 0.125;
    public static final double MAX_STAMP_SCALE = 8.0;
    private static final double STAMP_SCROLL_STEP = 1.2;

    private CanvasToolSettings() {}

    public static int penDiameter(Player player) {
        return player == null ? DEFAULT_PEN_DIAMETER : data(player).penDiameter();
    }

    /** Adjusts the brush by two pixels per wheel step and keeps it odd. */
    public static int adjustPenDiameter(Player player, double scroll) {
        if (player == null || scroll == 0.0) return DEFAULT_PEN_DIAMETER;
        CanvasToolSettingsData current = data(player);
        CanvasToolSettingsData next = current.withPenDiameter(
                adjustedPenDiameter(current.penDiameter(), scroll));
        store(player, next);
        return next.penDiameter();
    }

    public static double stampScale(Player player) {
        return player == null ? DEFAULT_STAMP_SCALE : data(player).stampScale();
    }

    public static double adjustStampScale(Player player, double scroll) {
        if (player == null || scroll == 0.0) return DEFAULT_STAMP_SCALE;
        CanvasToolSettingsData current = data(player);
        CanvasToolSettingsData next = current.withStampScale(
                adjustedStampScale(current.stampScale(), scroll));
        store(player, next);
        return next.stampScale();
    }

    /** Server-side application of a client update. Values are clamped again. */
    public static void applyFromClient(Player player, int penDiameter, double stampScale) {
        if (player == null) return;
        player.setData(ModAttachments.CANVAS_TOOL_SETTINGS.get(),
                new CanvasToolSettingsData(penDiameter, stampScale));
    }

    /** Pure brush adjustment used by the player-facing setters and by tests. */
    public static int adjustedPenDiameter(int current, double scroll) {
        int start = clampPenDiameter(current);
        if (scroll == 0.0) return start;

        int steps = Math.max(1, (int) Math.round(Math.abs(scroll)));
        int direction = scroll > 0.0 ? 1 : -1;
        int next = Math.max(MIN_PEN_DIAMETER,
                Math.min(MAX_PEN_DIAMETER, start + direction * steps * 2));
        if ((next & 1) == 0) next += direction > 0 ? -1 : 1;
        return clampPenDiameter(next);
    }

    /** Pure stamp scale adjustment used by the player-facing setters and by tests. */
    public static double adjustedStampScale(double current, double scroll) {
        double start = clampStampScale(current);
        if (scroll == 0.0) return start;
        return clampStampScale(start * Math.pow(STAMP_SCROLL_STEP, scroll));
    }

    public static int clampPenDiameter(int value) {
        int clamped = Math.max(MIN_PEN_DIAMETER, Math.min(MAX_PEN_DIAMETER, value));
        // Both bounds are odd, so rounding an even value up stays in range.
        return (clamped & 1) == 0 ? clamped + 1 : clamped;
    }

    public static double clampStampScale(double value) {
        if (!Double.isFinite(value)) return DEFAULT_STAMP_SCALE;
        return Math.max(MIN_STAMP_SCALE, Math.min(MAX_STAMP_SCALE, value));
    }

    private static CanvasToolSettingsData data(Player player) {
        return player.getData(ModAttachments.CANVAS_TOOL_SETTINGS.get());
    }

    /** Mirrors a change locally and forwards it to the server when on a client. */
    private static void store(Player player, CanvasToolSettingsData next) {
        if (next.equals(data(player))) return;
        player.setData(ModAttachments.CANVAS_TOOL_SETTINGS.get(), next);
        if (player.level().isClientSide) {
            PacketDistributor.sendToServer(new UpdateCanvasToolSettingsPacket(
                    next.penDiameter(), next.stampScale()));
        }
    }
}
