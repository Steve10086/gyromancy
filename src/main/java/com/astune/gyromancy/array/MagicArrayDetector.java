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
import com.astune.painter.event.ServerCanvasUpdateEvent;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.List;

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
        int id = GlyphMarker.nextGlyphId();
        GlyphMarker.markConsumed(glyph, id, level);

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
}
