package com.astune.gyromancy.array;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.PixelPos;
import com.astune.gyromancy.api.symbol.SymbolMatch;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.symbol.*;
import com.astune.gyromancy.symbol.FloodFillExtractor.ExtractedGlyph;
import com.astune.painter.api.CanvasData;
import com.astune.painter.event.ServerCanvasUpdateEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

/**
 * Listens for Pigmentum canvas updates and kicks off the full symbol
 * detection → flood fill → recognition pipeline.
 *
 * <p>This is the bridge between Pigmentum events and Phase 3's recognition engine.
 */
@EventBusSubscriber(modid = Gyromancy.MODID)
public final class MagicArrayDetector {

    private MagicArrayDetector() {}

    /** Glyph counter for unique IDs */
    private static int nextGlyphId = 1;

    @SubscribeEvent
    static void onCanvasUpdate(ServerCanvasUpdateEvent event) {
        CanvasData data = event.getCanvasData();
        if (data == null || data.faces().isEmpty()) return;

        Gyromancy.LOGGER.debug("[MagicArrayDetector] Canvas updated at {} ({} faces)",
                event.getPos(), data.faces().size());

        // ① Scan for mana pixel seeds
        List<PixelPos> seeds = ManaPixelDetector.scanForMana(
                event.getPlayer().level(), event.getPos(), data);

        if (seeds.isEmpty()) {
            Gyromancy.LOGGER.debug("[MagicArrayDetector] No mana seeds found at {}", event.getPos());
            return;
        }

        Gyromancy.LOGGER.debug("[MagicArrayDetector] Found {} mana seed(s) at {}",
                seeds.size(), event.getPos());

        // ② Submit each seed for flood fill extraction
        for (PixelPos seed : seeds) {
            if (event.getPlayer().level() instanceof net.minecraft.server.level.ServerLevel sl) {
                FloodFillScheduler.submit(sl, seed);
            }
        }
    }

    /**
     * Callback registered with FloodFillScheduler — invoked when a glyph
     * extraction completes. Runs recognition and marks consumed pixels.
     */
    static {
        FloodFillScheduler.onGlyphExtracted(MagicArrayDetector::onGlyphExtracted);
    }

    private static void onGlyphExtracted(
            net.minecraft.server.level.ServerLevel level, ExtractedGlyph glyph) {

        Gyromancy.LOGGER.debug("[MagicArrayDetector] Glyph extracted: {} pixels across {} blocks",
                glyph.pixels().size(), glyph.blockCount());

        // ③ Check for circular structure (outer ring)
        if (CircleStructureValidator.isCircularStructure(glyph)) {
            Gyromancy.LOGGER.debug("[MagicArrayDetector] Circular structure detected, validating...");
            boolean valid = CircleStructureValidator.validateCircle(glyph, level);
            Gyromancy.LOGGER.debug("[MagicArrayDetector] Circle validation: {}", valid);
        }

        // ④ Normalize and recognize
        List<SymbolMatch> matches = SymbolRecognizer.recognize(glyph);

        if (matches.isEmpty()) {
            Gyromancy.LOGGER.debug("[MagicArrayDetector] No template matched this glyph");
            return;
        }

        // ⑤ Mark consumed pixels
        int glyphId = nextGlyphId++;
        GlyphMarker.markConsumed(glyph, glyphId, level);

        // Log results
        Gyromancy.LOGGER.debug("[MagicArrayDetector] Glyph #{} recognized:", glyphId);
        for (SymbolMatch m : matches) {
            Gyromancy.LOGGER.debug("[MagicArrayDetector]   {} -> conf={} role={} rot={}°",
                    m.symbolId(),
                    String.format("%.3f", m.confidence()),
                    m.role(),
                    String.format("%.1f", m.rotationDegrees()));
        }

        // ⑥ If outer circle found, check for center symbol inside
        // (Phase 5 will handle compilation from here)
        boolean hasOuterCircle = matches.stream()
                .anyMatch(m -> m.role() == SymbolRole.OUTER_CIRCLE);
        boolean hasCenterSymbol = matches.stream()
                .anyMatch(m -> m.role() == SymbolRole.CENTER_SYMBOL);

        if (hasOuterCircle && hasCenterSymbol) {
            Gyromancy.LOGGER.debug("[MagicArrayDetector] Complete array detected! (circle + center) → Phase 5 compile");
        } else if (hasOuterCircle) {
            Gyromancy.LOGGER.debug("[MagicArrayDetector] Outer circle only — waiting for center symbol");
        } else if (hasCenterSymbol) {
            Gyromancy.LOGGER.debug("[MagicArrayDetector] Center symbol only — waiting for outer circle");
        }
    }
}
