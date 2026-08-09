package com.astune.gyromancy.wand;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.astune.gyromancy.canvas.CanvasGlyph;
import com.astune.gyromancy.registry.ModAttachments;
import com.astune.gyromancy.registry.ModDataComponents;
import net.minecraft.core.Direction;
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

    public static void project(Level level, Player player, ItemStack wand, WandLayout layout) {
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
            Vec3 center = player.getEyePosition().add(view.scale(layout.slotOffset(slot)));
            CanvasDocument document = cachedDocument.get();
            projectedGlyphs += document.glyphs().size();
            WandProjectionCanvasEntity projection = WandProjectionCanvasEntity.create(
                    serverLevel, center, facing, copyForProjection(document), player.getUUID(),
                    view,
                    player.getYRot() + 180.0F, player.getXRot(),
                    (float) layout.slotOffset(slot));
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
        List<WandProjectionCanvasEntity> owned = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof WandProjectionCanvasEntity projection
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
}
