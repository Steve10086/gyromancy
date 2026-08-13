package com.astune.gyromancy.entity.projection;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.geometry.SurfaceFrame;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.canvas.CanvasGlyph;
import com.astune.gyromancy.registry.ModAttachments;
import com.astune.gyromancy.registry.ModDataComponents;
import com.astune.gyromancy.wand.WandLayout;
import com.astune.gyromancy.wand.WandSlotSnapshots;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Creates the world-side canvas entities that make a wand's arrays executable. */
public final class WandProjectionService {
    private WandProjectionService() {}

    public static void project(Level level, Player player, ItemStack wand,
                               WandLayout layout, InteractionHand hand) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        discardOwned(serverLevel, player.getUUID());

        Vec3 view = player.getViewVector(1.0F).normalize();
        Direction facing = Direction.getNearest(view);
        WandSlotSnapshots snapshots = wand.getOrDefault(
                ModDataComponents.WAND_SLOT_SNAPSHOTS.get(), WandSlotSnapshots.EMPTY)
                .withSize(layout.slotCount());
        int arraysBefore = serverLevel.getData(ModAttachments.ARRAY_MANAGER)
                .getAllArrayObjs().size();
        int populatedSlots = 0;
        int spawnedSlots = 0;
        int projectedGlyphs = 0;
        for (int slot = 0; slot < layout.slotCount(); slot++) {
            java.util.Optional<CanvasDocument> cachedDocument = snapshots.get(slot).document();
            if (cachedDocument.isEmpty()) continue;
            populatedSlots++;
            Vec3 center = WandProjectionPose.targetCenter(
                    player.getEyePosition(), view, layout.slotOffset(slot), player.getYRot(),
                    WandProjectionPose.mirrorForHand(player.getMainArm(), hand));
            CanvasDocument document = cachedDocument.get();
            projectedGlyphs += document.glyphs().size();
            ProjectionCanvasEntity projection = ProjectionCanvasEntity.create(
                    serverLevel, center, facing, copyForProjection(document), player.getUUID(),
                    view,
                    player.getYRot() + 180.0F, player.getXRot(),
                    (float) layout.slotOffset(slot),
                    WandProjectionPose.mirrorForHand(player.getMainArm(), hand));
            if (!serverLevel.addFreshEntity(projection)) {
                Gyromancy.LOGGER.warn("[Wand] Failed to add projection entity for player {}",
                        player.getScoreboardName());
                continue;
            }
            spawnedSlots++;
            if (player instanceof ServerPlayer serverPlayer) {
                // The entity may not have entered the tracker yet when
                // CanvasCompileService.onPlaced broadcasts its snapshot.
                projection.sendSnapshot(serverPlayer, false);
            }
        }
        int arraysAfter = serverLevel.getData(ModAttachments.ARRAY_MANAGER)
                .getAllArrayObjs().size();
        Gyromancy.LOGGER.info(
                "[Wand] Projection complete for {}: slots={}/{}, glyphs={}, activeArrays={}->{}",
                player.getScoreboardName(), spawnedSlots, populatedSlots,
                projectedGlyphs, arraysBefore, arraysAfter);
    }

    public static void stop(Level level, Player player) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        int removed = discardOwned(serverLevel, player.getUUID());
        Gyromancy.LOGGER.info("[Wand] Projection stopped for {}: removed={}",
                player.getScoreboardName(), removed);
    }

    private static int discardOwned(ServerLevel level, UUID owner) {
        List<ProjectionCanvasEntity> owned = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ProjectionCanvasEntity projection
                    && owner.equals(projection.owner())) {
                owned.add(projection);
            }
        }
        owned.forEach(Entity::discard);
        return owned.size();
    }

    /** Prevents cached glyph UUIDs from colliding when one canvas is projected repeatedly. */
    private static CanvasDocument copyForProjection(CanvasDocument source) {
        Map<UUID, UUID> ids = new HashMap<>();
        List<CanvasGlyph> glyphs = source.glyphs().stream().map(glyph -> {
            UUID next = UUID.randomUUID();
            ids.put(glyph.glyphUuid(), next);
            return new CanvasGlyph(next, glyph.symbolId(), glyph.confidence(), glyph.role(),
                    glyph.frontX(), glyph.frontY(), glyph.length(), glyph.width(),
                    glyph.minX(), glyph.maxX(), glyph.minY(), glyph.maxY(), glyph.cells());
        }).toList();
        // A projection is compiled from its copied glyphs when it appears in
        // the world. Array records are only a legacy/material cache and must
        // not become a second activation path.
        return source.withCompileCache(glyphs, List.of());
    }

    /**
     * Copies only selected recognized glyphs into a new projection document.
     * The raster is filtered as well, so unselected source strokes cannot leak
     * into the projected plane.
     */
    public static CanvasDocument copySelectedGlyphsForProjection(
            CanvasDocument source, Set<UUID> selectedGlyphIds) {
        List<CanvasGlyph> glyphs = source.glyphs().stream()
                .filter(glyph -> selectedGlyphIds.contains(glyph.glyphUuid()))
                .map(glyph -> {
                    UUID next = UUID.randomUUID();
                    return new CanvasGlyph(next, glyph.symbolId(), glyph.confidence(), glyph.role(),
                            glyph.frontX(), glyph.frontY(), glyph.length(), glyph.width(),
                            glyph.minX(), glyph.maxX(), glyph.minY(), glyph.maxY(), glyph.cells());
                })
                .toList();

        int[] colors = source.colors();
        int[] effects = source.strokeEffects();
        boolean[] selectedCells = new boolean[effects.length];
        for (CanvasGlyph glyph : source.glyphs()) {
            if (!selectedGlyphIds.contains(glyph.glyphUuid())) continue;
            for (int cell : glyph.cells()) {
                if (cell >= 0 && cell < selectedCells.length) selectedCells[cell] = true;
            }
        }
        for (int i = 0; i < selectedCells.length; i++) {
            if (!selectedCells[i]) {
                colors[i] = 0;
                effects[i] = 0;
            }
        }
        return new CanvasDocument(source.physicalWidth(), source.physicalHeight(),
                source.resolutionScale(), colors, effects, glyphs, List.of());
    }

    /**
     * Copies selected glyphs while mapping the source outer-circle rectangle to
     * the complete output canvas. The output canvas keeps the source canvas'
     * physical size; only its local glyph geometry and raster are expanded.
     */
    public static CanvasDocument copySelectedGlyphsForProjection(
            CanvasDocument source,
            Set<UUID> selectedGlyphIds,
            PositionedGlyph sourceCircle,
            SurfaceFrame sourceFrame) {
        int width = source.resolutionWidth();
        int height = source.resolutionHeight();
        SurfaceFrame.SurfaceBounds circle = sourceCircle.boundsOn(sourceFrame);

        double left = clamp(0.5 + circle.minU() / source.physicalWidth());
        double right = clamp(0.5 + circle.maxU() / source.physicalWidth());
        double top = clamp(0.5 - circle.maxV() / source.physicalHeight());
        double bottom = clamp(0.5 - circle.minV() / source.physicalHeight());
        double sourceWidth = right - left;
        double sourceHeight = bottom - top;
        if (sourceWidth <= 1.0E-6 || sourceHeight <= 1.0E-6) {
            return copySelectedGlyphsForProjection(source, selectedGlyphIds);
        }

        double scaleX = 1.0 / sourceWidth;
        double scaleY = 1.0 / sourceHeight;
        int[] sourceColors = source.colors();
        int[] sourceEffects = source.strokeEffects();
        int[] colors = new int[sourceColors.length];
        int[] effects = new int[sourceEffects.length];

        List<CanvasGlyph> glyphs = source.glyphs().stream()
                .filter(glyph -> selectedGlyphIds.contains(glyph.glyphUuid()))
                .map(glyph -> new CanvasGlyph(
                        UUID.randomUUID(),
                        glyph.symbolId(),
                        glyph.confidence(),
                        glyph.role(),
                        glyph.frontX() * scaleX,
                        glyph.frontY() * scaleY,
                        glyph.length() * Math.max(scaleX, scaleY),
                        glyph.width() * Math.max(scaleX, scaleY),
                        mapX(glyph.minX(), left, scaleX),
                        mapX(glyph.maxX(), left, scaleX),
                        mapY(glyph.minY(), top, scaleY),
                        mapY(glyph.maxY(), top, scaleY),
                        mapCells(glyph, width, height, left, top, scaleX, scaleY,
                                sourceColors, sourceEffects, colors, effects)))
                .toList();

        return new CanvasDocument(source.physicalWidth(), source.physicalHeight(),
                source.resolutionScale(), colors, effects, glyphs, List.of());
    }

    private static int[] mapCells(
            CanvasGlyph glyph,
            int width,
            int height,
            double left,
            double top,
            double scaleX,
            double scaleY,
            int[] sourceColors,
            int[] sourceEffects,
            int[] colors,
            int[] effects) {
        java.util.ArrayList<Integer> mapped = new java.util.ArrayList<>();
        for (int cell : glyph.cells()) {
            if (cell < 0 || cell >= width * height) continue;
            int sourceX = cell % width;
            int sourceY = cell / width;
            double x0 = (sourceX / (double) width - left) * scaleX;
            double x1 = ((sourceX + 1.0) / width - left) * scaleX;
            double y0 = (sourceY / (double) height - top) * scaleY;
            double y1 = ((sourceY + 1.0) / height - top) * scaleY;
            int minX = Math.max(0, (int) Math.floor(x0 * width));
            int maxX = Math.min(width - 1, (int) Math.ceil(x1 * width) - 1);
            int minY = Math.max(0, (int) Math.floor(y0 * height));
            int maxY = Math.min(height - 1, (int) Math.ceil(y1 * height) - 1);
            if (minX > maxX || minY > maxY) continue;

            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    int destination = y * width + x;
                    if (!mapped.contains(destination)) mapped.add(destination);
                    colors[destination] = sourceColors[cell];
                    effects[destination] = Math.max(effects[destination], sourceEffects[cell]);
                }
            }
        }
        return mapped.stream().mapToInt(Integer::intValue).toArray();
    }

    private static double mapX(double value, double left, double scale) {
        return clamp((value - left) * scale);
    }

    private static double mapY(double value, double top, double scale) {
        return clamp((value - top) * scale);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
