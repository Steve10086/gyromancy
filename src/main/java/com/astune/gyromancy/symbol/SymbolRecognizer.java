package com.astune.gyromancy.symbol;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.SymbolMatch;
import com.astune.gyromancy.api.symbol.SymbolTemplate;
import com.astune.gyromancy.registry.GyromancyRegistries;
import com.astune.gyromancy.symbol.FloodFillExtractor.ExtractedGlyph;
import net.minecraft.core.Registry;
import org.slf4j.Logger;

import java.util.*;

/**
 * Main entry point for symbol recognition.
 *
 * <p>Orchestrates the full pipeline for a single extracted glyph:
 * <ol>
 *   <li>Normalize the extracted glyph to a 32×32 binary grid</li>
 *   <li>Match against all registered templates via {@link SkeletonMatcher}</li>
 *   <li>Keep matches above the confidence threshold</li>
 *   <li>Return the resulting {@link SymbolMatch}(es)</li>
 * </ol>
 */
public final class SymbolRecognizer {

    private static final Logger LOGGER = Gyromancy.LOGGER;

    private SymbolRecognizer() {}

    public record RecognizerConfig(
            float confidenceThreshold
    ) {
        public static final RecognizerConfig DEFAULT = new RecognizerConfig(0.40f);
    }

    /**
     * Recognizes an extracted glyph by normalizing and matching against all templates.
     */
    public static List<SymbolMatch> recognize(
            ExtractedGlyph glyph,
            Registry<SymbolTemplate> symbolRegistry,
            RecognizerConfig config) {

        if (glyph.pixels().isEmpty()) {
            LOGGER.debug("[SymbolRecognizer] Empty glyph — no pixels to recognize");
            return Collections.emptyList();
        }

        int[][] normalized = FloodFillExtractor.normalizeGlyph(glyph);
        int glyphArea = glyph.pixels().size();

        LOGGER.debug("[SymbolRecognizer] Matching glyph: {} pixels across {} blocks (bbox: {},{} → {},{})",
                glyphArea, glyph.blockCount(),
                String.format("%.1f", glyph.minWorldX()), String.format("%.1f", glyph.minWorldY()),
                String.format("%.1f", glyph.maxWorldX()), String.format("%.1f", glyph.maxWorldY()));

        printDebugGrid(normalized);

        // Single skeleton pipeline run → batch match against all templates
        SkeletonMatcher matcher = SkeletonMatcher.getInstance();
        List<SkeletonMatcher.Match> results = matcher.recognize(normalized);

        // Build index: templateId → SymbolTemplate
        Map<String, SymbolTemplate> tplIndex = new HashMap<>();
        for (SymbolTemplate t : symbolRegistry) tplIndex.put(t.id().toString(), t);

        List<SymbolMatch> matches = new ArrayList<>();
        for (SkeletonMatcher.Match result : results) {
            if (result.confidence() < config.confidenceThreshold()) continue;

            SymbolTemplate template = tplIndex.get(result.templateId().toString());
            if (template == null) continue;

            float centerX = (float) ((glyph.minWorldX() + glyph.maxWorldX()) / 2.0);
            float centerY = (float) ((glyph.minWorldY() + glyph.maxWorldY()) / 2.0);

            matches.add(new SymbolMatch(
                    template.id(),
                    result.confidence(),
                    0f, false, 1f,
                    centerX, centerY,
                    template.defaultRole()
            ));
        }

        if (!matches.isEmpty()) {
            SymbolMatch best = matches.getFirst();
            LOGGER.debug("[SymbolRecognizer] >> BEST MATCH: {} (conf={}, role={})",
                    best.symbolId(), String.format("%.3f", best.confidence()), best.role());
        } else {
            LOGGER.debug("[SymbolRecognizer] >> NO MATCH — no template exceeded confidence threshold {}",
                    String.format("%.2f", config.confidenceThreshold()));
        }

        return matches;
    }

    public static List<SymbolMatch> recognize(ExtractedGlyph glyph) {
        return recognize(glyph, GyromancyRegistries.SYMBOL, RecognizerConfig.DEFAULT);
    }

    public static List<SymbolMatch> recognize(ExtractedGlyph glyph, float confidenceThreshold) {
        return recognize(glyph, GyromancyRegistries.SYMBOL,
                new RecognizerConfig(confidenceThreshold));
    }

    // ═══════════════════════ Debug ═══════════════════════

    static void printDebugGrid(int[][] pattern) {
        StringBuilder sb = new StringBuilder("\n[SymbolRecognizer] Glyph 8×8:\n");
        for (int by = 0; by < 8; by++) {
            sb.append("  ");
            for (int bx = 0; bx < 8; bx++) {
                int count = 0;
                for (int y = by * 4; y < (by + 1) * 4; y++)
                    for (int x = bx * 4; x < (bx + 1) * 4; x++)
                        if (pattern[y][x] != 0) count++;
                char c = count >= 12 ? '█' : count >= 8 ? '▓' : count >= 4 ? '▒' : count >= 1 ? '░' : '·';
                sb.append(c);
            }
            sb.append('\n');
        }
        LOGGER.debug(sb.toString());
    }
}
