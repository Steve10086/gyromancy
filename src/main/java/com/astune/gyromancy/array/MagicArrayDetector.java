package com.astune.gyromancy.array;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.array.MagicArrayManager;
import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.PositionedGlyph;
import com.astune.gyromancy.api.symbol.SymbolMatch;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.registry.ModAttachments;
import com.astune.gyromancy.symbol.*;
import com.astune.gyromancy.symbol.FloodFillExtractor.ExtractedGlyph;
import com.astune.painter.api.CanvasData;
import com.astune.painter.api.CanvasDataHolder;
import com.astune.painter.block.CanvasBlockEntity;
import com.astune.painter.event.ServerCanvasUpdateEvent;
import com.astune.painter.network.SyncCanvasPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bridge between Pigmentum canvas events and the symbol recognition pipeline.
 *
 * <p>Flow:
 * <ol>
 *   <li>{@code ServerCanvasUpdateEvent} → scan for mana seeds</li>
 *   <li>Submit seeds to {@link FloodFillScheduler} (with merging)</li>
 *   <li>On glyph extracted → recognize</li>
 *   <li>Interior validation → mark ONLY on success</li>
 *   <li>Store recognized glyphs as {@link PositionedGlyph}</li>
 *   <li>If outer circle + inner glyphs → ready for Phase 5</li>
 * </ol>
 */
@EventBusSubscriber(modid = Gyromancy.MODID)
public final class MagicArrayDetector {

    private MagicArrayDetector() {}

    static {
        FloodFillScheduler.onGlyphExtracted(MagicArrayDetector::onGlyphExtracted);
    }

    @SubscribeEvent
    static void onCanvasUpdate(ServerCanvasUpdateEvent event) {
        CanvasData data = event.getCanvasData();
        if (data == null || data.faces().isEmpty()) return;

        Gyromancy.LOGGER.debug("[MagicArrayDetector] Canvas updated at {} ({} faces)",
                event.getPos(), data.faces().size());

        List<PixelPos> seeds = ManaPixelDetector.scanForMana(
                event.getPlayer().level(), event.getPos(), data);

        if (seeds.isEmpty()) return;

        Gyromancy.LOGGER.debug("[MagicArrayDetector] {} mana seed(s) at {}", seeds.size(), event.getPos());

        if (event.getPlayer().level() instanceof ServerLevel sl) {
            FloodFillScheduler.submitBatch(sl, seeds);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Glyph completion callback
    // ═══════════════════════════════════════════════════════════════

    private static void onGlyphExtracted(ServerLevel level, ExtractedGlyph glyph) {
        Gyromancy.LOGGER.debug("[MagicArrayDetector] Glyph extracted: {} pixels across {} blocks",
                glyph.pixels().size(), glyph.blockCount());

        // 1. Recognize
        List<SymbolMatch> matches = SymbolRecognizer.recognize(glyph);
        if (matches.isEmpty()) {
            Gyromancy.LOGGER.debug("[MagicArrayDetector] No match — pixels NOT marked");
            return;
        }

        SymbolMatch best = matches.getFirst();
        SymbolRole role = best.role();
        Gyromancy.LOGGER.debug("[MagicArrayDetector] Best match: {} conf={} role={}",
                best.symbolId(), String.format("%.3f", best.confidence()), role);

        // 2. Interior validation + conditional marking
        if (role == SymbolRole.CENTER_SYMBOL || role == SymbolRole.PARAMETER_RUNE) {
            handleRuneMatch(level, glyph, best);

        } else if (role == SymbolRole.OUTER_CIRCLE) {
            handleCircleMatch(level, glyph, best);
        }
    }

    // ═══════════════════════ RUNE ═══════════════════════
    private static void handleRuneMatch(ServerLevel level, ExtractedGlyph glyph, SymbolMatch best) {
        if (InteriorValidator.hasRawManaInside(glyph, level)) {
            Gyromancy.LOGGER.debug("[MagicArrayDetector] Rune {} REJECTED: raw mana inside",
                    best.symbolId());
            return;
        }

        // Clean → mark + store
        int colorIndex = getGlyphColorIndex(best.symbolId());
        int id = GlyphMarker.nextGlyphId();
        GlyphMarker.markConsumed(glyph, colorIndex, id, level);

        // Sync modified canvas back to clients so they see the consumed marks
        syncAffectedCanvases(glyph, level);

        PositionedGlyph pg = new PositionedGlyph(
                id, best.symbolId(), best.confidence(), best.role(),
                glyph.pixels().iterator().next().pos(), // representative position
                glyph.minWorldX(), glyph.maxWorldX(),
                glyph.minWorldY(), glyph.maxWorldY()
        );

        MagicArrayManager mgr = level.getData(ModAttachments.ARRAY_MANAGER);
        mgr.registerGlyph(pg);

        Gyromancy.LOGGER.debug("[MagicArrayDetector] Rune {} ACCEPTED → glyph #{} stored",
                best.symbolId(), id);
    }

    // ═══════════════════════ CIRCLE ═══════════════════════
    private static void handleCircleMatch(ServerLevel level, ExtractedGlyph glyph, SymbolMatch best) {
        if (InteriorValidator.hasRawManaInside(glyph, level)) {
            Gyromancy.LOGGER.debug("[MagicArrayDetector] Circle REJECTED: raw mana inside");
            return;
        }

        MagicArrayManager mgr = level.getData(ModAttachments.ARRAY_MANAGER);
        List<PositionedGlyph> innerGlyphs = InteriorValidator.findGlyphsInside(
                glyph, level, mgr.getGlyphIndex());

        if (!innerGlyphs.isEmpty()) {
            Gyromancy.LOGGER.debug("[MagicArrayDetector] Circle + {} inner glyph(s) → Phase 5 ready!",
                    innerGlyphs.size());
            for (PositionedGlyph pg : innerGlyphs) {
                Gyromancy.LOGGER.debug("[MagicArrayDetector]   inner: {} conf={} at {}",
                        pg.symbolId(), String.format("%.3f", pg.confidence()), pg.worldPos());
            }
            // Phase 5 hook will go here
        } else {
            Gyromancy.LOGGER.debug("[MagicArrayDetector] Circle found, no inner glyphs yet — waiting");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Color mapping — symbol → glyph color index
    // ═══════════════════════════════════════════════════════════════

    /**
     * Maps a matched symbol ID to its glyph color index.
     * The index is encoded into the glyph_id effect layer and decoded
     * on the client by {@code GlyphImageProvider} for colored rendering.
     */
    private static int getGlyphColorIndex(ResourceLocation symbolId) {
        String name = symbolId.getPath();
        return switch (name) {
            case "fire"  -> GlyphMarker.COLOR_FIRE;   // → Red   0xFFFF0000
            case "water" -> GlyphMarker.COLOR_WATER;  // → Blue  0xFF0000FF
            case "earth" -> GlyphMarker.COLOR_EARTH;  // → Brown 0xFF8B4513
            default      -> GlyphMarker.COLOR_FIRE;   // fallback: red
        };
    }

    // ═══════════════════════════════════════════════════════════════
    // Canvas sync — pushes modified canvas data to clients
    // ═══════════════════════════════════════════════════════════════

    /**
     * Syncs all canvas blocks touched by a glyph back to clients.
     * Called after {@link GlyphMarker#markConsumed} modifies effect layers
     * so clients can see the consumed pixel markings.
     */
    private static void syncAffectedCanvases(ExtractedGlyph glyph, ServerLevel level) {
        Set<BlockPos> uniquePositions = glyph.pixels().stream()
                .map(PixelPos::pos)
                .collect(Collectors.toSet());

        for (BlockPos pos : uniquePositions) {
            syncCanvasAt(level, pos);
        }
    }

    /**
     * Syncs a single canvas block to all clients tracking its chunk.
     * Sends the full CanvasData including effect layer changes from glyph marking.
     */
    private static void syncCanvasAt(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof CanvasDataHolder holder)) return;

        CanvasData data = holder.painter$getCanvasData();
        BlockState state = level.getBlockState(pos);
        BlockState mimicked = be instanceof CanvasBlockEntity canvasBE
                ? canvasBE.getMimickedState()
                : null;

        PacketDistributor.sendToPlayersTrackingChunk(
                level, new ChunkPos(pos),
                new SyncCanvasPacket(pos, data, Optional.ofNullable(mimicked), false)
        );
        level.setBlocksDirty(pos, state, state);
    }
}
