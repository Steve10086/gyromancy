package com.astune.gyromancy.canvas;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-character editor settings shared by every canvas-based screen.
 *
 * <p>These values describe the local editing tool, not the item stack. The
 * carved pixels are still submitted normally; only the next editor gesture
 * needs this client-side state.
 */
public final class CanvasToolSettings {
    public static final int DEFAULT_PEN_DIAMETER = 1;
    public static final int MIN_PEN_DIAMETER = 1;
    public static final int MAX_PEN_DIAMETER = 31;
    public static final double DEFAULT_STAMP_SCALE = 1.0;
    public static final double MIN_STAMP_SCALE = 0.125;
    public static final double MAX_STAMP_SCALE = 8.0;
    private static final double STAMP_SCROLL_STEP = 1.2;

    private static final Map<UUID, Settings> BY_PLAYER = new ConcurrentHashMap<>();

    private CanvasToolSettings() {}

    public static int penDiameter(Player player) {
        return penDiameter(player == null ? null : player.getUUID());
    }

    public static int penDiameter(UUID playerId) {
        return playerId == null
                ? DEFAULT_PEN_DIAMETER
                : settings(playerId).penDiameter;
    }

    /** Adjusts the brush by two pixels per wheel step and keeps it odd. */
    public static int adjustPenDiameter(Player player, double scroll) {
        UUID playerId = player == null ? null : player.getUUID();
        return adjustPenDiameter(playerId, scroll);
    }

    public static int adjustPenDiameter(UUID playerId, double scroll) {
        if (playerId == null || scroll == 0.0) return DEFAULT_PEN_DIAMETER;

        Settings settings = settings(playerId);
        int steps = Math.max(1, (int) Math.round(Math.abs(scroll)));
        int direction = scroll > 0.0 ? 1 : -1;
        int next = settings.penDiameter + direction * steps * 2;
        next = Math.max(MIN_PEN_DIAMETER, Math.min(MAX_PEN_DIAMETER, next));
        if ((next & 1) == 0) next += direction > 0 ? -1 : 1;
        settings.penDiameter = Math.max(MIN_PEN_DIAMETER,
                Math.min(MAX_PEN_DIAMETER, next));
        return settings.penDiameter;
    }

    public static double stampScale(Player player) {
        return stampScale(player == null ? null : player.getUUID());
    }

    public static double stampScale(UUID playerId) {
        return playerId == null
                ? DEFAULT_STAMP_SCALE
                : settings(playerId).stampScale;
    }

    public static double adjustStampScale(Player player, double scroll) {
        UUID playerId = player == null ? null : player.getUUID();
        return adjustStampScale(playerId, scroll);
    }

    public static double adjustStampScale(UUID playerId, double scroll) {
        if (playerId == null || scroll == 0.0) return DEFAULT_STAMP_SCALE;

        Settings settings = settings(playerId);
        settings.stampScale = clamp(settings.stampScale
                        * Math.pow(STAMP_SCROLL_STEP, scroll),
                MIN_STAMP_SCALE,
                MAX_STAMP_SCALE);
        return settings.stampScale;
    }

    /** Clears all client-side editor settings when the current character logs out. */
    public static void clear() {
        BY_PLAYER.clear();
    }

    private static Settings settings(UUID playerId) {
        return BY_PLAYER.computeIfAbsent(playerId, ignored -> new Settings());
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class Settings {
        private int penDiameter = DEFAULT_PEN_DIAMETER;
        private double stampScale = DEFAULT_STAMP_SCALE;
    }
}
