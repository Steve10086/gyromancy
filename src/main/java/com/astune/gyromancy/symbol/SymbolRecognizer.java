package com.astune.gyromancy.symbol;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.SymbolMatch;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.api.symbol.SymbolTemplate;
import com.astune.gyromancy.registry.GyromancyRegistries;
import com.astune.gyromancy.symbol.FloodFillExtractor.ExtractedGlyph;
import com.astune.gyromancy.symbol.GeometricMatcher.MatchResult;
import net.minecraft.core.Registry;

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

    private SymbolRecognizer() {}

    /**
     * Configuration for symbol recognition.
     */
    public record RecognizerConfig(
            /** Minimum confidence to accept a match (default 0.65) */
            float confidenceThreshold
    ) {
        public static final RecognizerConfig DEFAULT = new RecognizerConfig(0.65f);
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
            return Collections.emptyList();
        }

        // 1. Normalize the glyph to 32×32
        int[][] normalized = FloodFillExtractor.normalizeGlyph(glyph);

        // 2. Match against all templates from both registries
        List<ScoredMatch> candidates = new ArrayList<>();

        for (SymbolTemplate template : symbolRegistry) {
            MatchResult result = GeometricMatcher.match(normalized, template);
            if (result.confidence() >= config.confidenceThreshold()) {
                candidates.add(new ScoredMatch(template, result));
            }
        }

        for (SymbolTemplate template : runeRegistry) {
            MatchResult result = GeometricMatcher.match(normalized, template);
            if (result.confidence() >= config.confidenceThreshold()) {
                candidates.add(new ScoredMatch(template, result));
            }
        }

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
            Gyromancy.LOGGER.debug("[SymbolRecognizer] Recognized {}: confidence={} role={}",
                    matches.getFirst().symbolId(),
                    String.format("%.2f", matches.getFirst().confidence()),
                    matches.getFirst().role());
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
}
