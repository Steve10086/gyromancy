package com.astune.gyromancy.symbol;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.SymbolMatch;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.api.symbol.SymbolTemplate;
import com.astune.gyromancy.registry.GyromancyRegistries;
import com.astune.gyromancy.symbol.FloodFillExtractor.ExtractedGlyph;
import com.astune.gyromancy.symbol.GeometricMatcher.MatchResult;
import net.minecraft.core.Registry;
import org.slf4j.Logger;

import java.util.*;

/**
 * Main entry point for symbol recognition.
 *
 * <p>Orchestrates the full pipeline for a single extracted glyph:
 * <ol>
 *   <li>Normalize the extracted glyph to a 32×32 binary grid</li>
 *   <li>Match against all registered templates in both SYMBOL and RUNE registries</li>
 *   <li>Keep the best match above the confidence threshold</li>
 *   <li>Return the resulting {@link SymbolMatch}(es)</li>
 * </ol>
 */
public final class SymbolRecognizer {

    private static final Logger LOGGER = Gyromancy.LOGGER;

    private SymbolRecognizer() {}

    /**
     * Configuration for symbol recognition.
     */
    public record RecognizerConfig(
            /** Minimum confidence to accept a match (default 0.65) */
            float confidenceThreshold
    ) {
        public static final RecognizerConfig DEFAULT = new RecognizerConfig(0.5f);
    }

    /**
     * Recognizes an extracted glyph by normalizing and matching against all templates.
     *
     * @param glyph          the cross-block extracted glyph
     * @param symbolRegistry the SYMBOL template registry (functional symbols + outer circles)
     * @param runeRegistry   the RUNE template registry (parameter runes)
     * @param config         recognition configuration
     * @return the best SymbolMatch for each detected symbol, sorted by confidence descending;
     *         empty list if nothing recognized
     */
    public static List<SymbolMatch> recognize(
            ExtractedGlyph glyph,
            Registry<SymbolTemplate> symbolRegistry,
            Registry<SymbolTemplate> runeRegistry,
            RecognizerConfig config) {

        if (glyph.pixels().isEmpty()) {
            LOGGER.debug("[SymbolRecognizer] Empty glyph — no pixels to recognize");
            return Collections.emptyList();
        }

        // 1. Normalize the glyph to 32×32
        int[][] normalized = FloodFillExtractor.normalizeGlyph(glyph);
        int glyphArea = glyph.pixels().size();
        int glyphBlocks = glyph.blockCount();

        LOGGER.debug("[SymbolRecognizer] Matching glyph: {} pixels across {} blocks (bbox: {},{} → {},{})",
                glyphArea, glyphBlocks,
                String.format("%.1f", glyph.minWorldX()), String.format("%.1f", glyph.minWorldY()),
                String.format("%.1f", glyph.maxWorldX()), String.format("%.1f", glyph.maxWorldY()));

        printDebugGrid(normalized);

        // 2. Match against all templates from both registries
        List<ScoredMatch> candidates = new ArrayList<>();
        int symbolTotal = 0, runeTotal = 0;
        int symbolHits = 0, runeHits = 0;

        for (SymbolTemplate template : symbolRegistry) {
            symbolTotal++;
            MatchResult result = GeometricMatcher.match(normalized, template);
            if (result.confidence() >= config.confidenceThreshold()) {
                symbolHits++;
                candidates.add(new ScoredMatch(template, result));
            }
            LOGGER.debug("[SymbolRecognizer] {} SYMBOL {} | conf={} tf={}",
                    result.confidence() >= config.confidenceThreshold() ? "+" : "-",
                    template.id(),
                    fmt(result.confidence()),
                    fmt(result.tfScore()));
        }

        for (SymbolTemplate template : runeRegistry) {
            runeTotal++;
            MatchResult result = GeometricMatcher.match(normalized, template);
            if (result.confidence() >= config.confidenceThreshold()) {
                runeHits++;
                candidates.add(new ScoredMatch(template, result));
            }
            LOGGER.debug("[SymbolRecognizer] {} RUNE   {} | conf={} tf={}",
                    result.confidence() >= config.confidenceThreshold() ? "+" : "-",
                    template.id(),
                    fmt(result.confidence()),
                    fmt(result.tfScore()));
        }

        LOGGER.debug("[SymbolRecognizer] Scanned {} SYMBOL ({} hits) + {} RUNE ({} hits) -> {} candidates",
                symbolTotal, symbolHits, runeTotal, runeHits, candidates.size());

        // 3. Sort by confidence descending
        candidates.sort((a, b) -> Float.compare(b.result.confidence(), a.result.confidence()));

        // 4. Convert to SymbolMatch records
        List<SymbolMatch> matches = new ArrayList<>();
        for (ScoredMatch candidate : candidates) {
            SymbolTemplate template = candidate.template;
            MatchResult result = candidate.result;

            // Compute normalized center position from the glyph's world bounds
            float centerX = (float) ((glyph.minWorldX() + glyph.maxWorldX()) / 2.0);
            float centerY = (float) ((glyph.minWorldY() + glyph.maxWorldY()) / 2.0);

            SymbolMatch match = new SymbolMatch(
                    template.id(),
                    result.confidence(),
                    result.rotationDegrees(),
                    result.mirrored(),
                    result.scale(),
                    centerX, centerY,
                    template.defaultRole()
            );
            matches.add(match);
        }

        if (!matches.isEmpty()) {
            SymbolMatch best = matches.getFirst();
            LOGGER.debug("[SymbolRecognizer] >> BEST MATCH: {} (conf={}, role={}, rot={}°, mir={}, scale={})",
                    best.symbolId(),
                    String.format("%.3f", best.confidence()),
                    best.role(),
                    String.format("%.1f", best.rotationDegrees()),
                    best.mirrored(),
                    String.format("%.2f", best.scale()));
        } else {
            LOGGER.debug("[SymbolRecognizer] >> NO MATCH — no template exceeded confidence threshold {}",
                    String.format("%.2f", config.confidenceThreshold()));
        }

        return matches;
    }

    /**
     * Convenience overload using the mod's built-in registries.
     */
    public static List<SymbolMatch> recognize(ExtractedGlyph glyph) {
        return recognize(
                glyph,
                GyromancyRegistries.SYMBOL,
                GyromancyRegistries.RUNE,
                RecognizerConfig.DEFAULT
        );
    }

    /**
     * Convenience overload with custom threshold.
     */
    public static List<SymbolMatch> recognize(ExtractedGlyph glyph, float confidenceThreshold) {
        return recognize(
                glyph,
                GyromancyRegistries.SYMBOL,
                GyromancyRegistries.RUNE,
                new RecognizerConfig(confidenceThreshold)
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // Internal
    // ═══════════════════════════════════════════════════════════════

    private record ScoredMatch(SymbolTemplate template, MatchResult result) {}

    /** Downsample 32×32 → 8×8 and print to debug log */
    static void printDebugGrid(int[][] pattern) {
        StringBuilder sb = new StringBuilder("\n[SymbolRecognizer] Glyph 8×8:\n");
        for (int by = 0; by < 8; by++) {
            sb.append("  ");
            for (int bx = 0; bx < 8; bx++) {
                int count = 0;
                for (int y = by * 4; y < (by + 1) * 4; y++)
                    for (int x = bx * 4; x < (bx + 1) * 4; x++)
                        if (pattern[y][x] != 0) count++;
                // █≥12  ▓≥8  ▒≥4  ░≥1  ·0
                char c = count >= 12 ? '█' : count >= 8 ? '▓' : count >= 4 ? '▒' : count >= 1 ? '░' : '·';
                sb.append(c);
            }
            sb.append('\n');
        }
        LOGGER.debug(sb.toString());
    }

    private static String fmt(float v) {
        return String.format("%.3f", v);
    }
}
