package com.astune.gyromancy.symbol;

import com.astune.gyromancy.Config;
import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.SymbolMatch;
import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.api.symbol.SymbolTemplate;
import com.astune.gyromancy.registry.GyromancyRegistries;
import com.astune.gyromancy.symbol.FloodFillExtractor.ExtractedGlyph;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main entry point for symbol recognition.
 *
 * <p>Orchestrates the full pipeline for a single extracted glyph:
 * <ol>
 *   <li>Rasterize the extracted glyph to its raw binary matrix</li>
 *   <li>Try the strict clipped-秘文 {@link SecretTextMatcher} path</li>
 *   <li>Fall back to all registered templates via {@link SkeletonMatcher}</li>
 *   <li>Keep matches returned by {@link SkeletonMatcher}'s hard/soft threshold pipeline</li>
 *   <li>Return ordinary {@link SymbolMatch}(es), including 秘文 as parameter-role matches</li>
 * </ol>
 */
public final class SymbolRecognizer {

    private static final Logger LOGGER = Gyromancy.LOGGER;
    private static final AtomicInteger DEBUG_IMAGE_ID = new AtomicInteger();

    private SymbolRecognizer() {}

    public record RecognizerConfig(
            float confidenceThreshold,
            boolean saveDebugImage
    ) {
        /**
         * Kept for API compatibility; SkeletonMatcher now owns all match thresholds.
         * Raw glyph export also requires Config.ENABLE_SYMBOL_MATCH_DEBUG_OUTPUT.
         */
        public static final RecognizerConfig DEFAULT = new RecognizerConfig(0.40f, true);
        public static final RecognizerConfig CLIENT_PREVIEW = new RecognizerConfig(0.40f, false);

        public RecognizerConfig(float confidenceThreshold) {
            this(confidenceThreshold, true);
        }
    }

    /**
     * Complete result of one recognition pass. Secret-text matches use an
     * independent {@link SecretTextSymbol} object backed by the shared
     * {@link SecretText} enum and are exposed as parameter-role symbol
     * matches. Their detailed match records also provide a {@code
     * ParameterRune} representation.
     */
    public record RecognitionResult(
            List<SymbolMatch> symbolMatches,
            List<SecretTextMatcher.Match> secretTextMatches
    ) {
        public RecognitionResult {
            symbolMatches = List.copyOf(symbolMatches);
            secretTextMatches = List.copyOf(secretTextMatches);
        }

        public boolean hasSecretText() {
            return !secretTextMatches.isEmpty();
        }
    }

    /**
     * Recognizes an extracted glyph by rasterizing and matching against all templates.
     */
    public static List<SymbolMatch> recognize(
            ExtractedGlyph glyph,
            Registry<SymbolTemplate> symbolRegistry,
            RecognizerConfig config) {
        return recognizeDetailed(glyph, symbolRegistry, config).symbolMatches();
    }

    /**
     * Recognizes an extracted glyph and preserves either the exact 秘文
     * result or the ordinary symbol result.
     *
     * <p>The ordinary {@link #recognize(ExtractedGlyph, Registry, RecognizerConfig)}
     * method remains source-compatible for existing callers and returns the
     * same {@link SymbolMatch} view. Callers that need the concrete 秘文
     * object or its {@code ParameterRune} view can inspect
     * {@link RecognitionResult#secretTextMatches()}.
     */
    public static RecognitionResult recognizeDetailed(
            ExtractedGlyph glyph,
            Registry<SymbolTemplate> symbolRegistry,
            RecognizerConfig config) {

        if (glyph.pixels().isEmpty()) {
            LOGGER.debug("[SymbolRecognizer] Empty glyph - no pixels to recognize");
            return new RecognitionResult(List.of(), List.of());
        }

        int[][] rawMatrix = FloodFillExtractor.rawGlyphMatrix(glyph);
        int maxDirectionX = Math.max(0, rawMatrix[0].length - 1);
        int maxDirectionY = Math.max(0, rawMatrix.length - 1);
        int glyphArea = glyph.pixels().size();

        LOGGER.debug("[SymbolRecognizer] Matching glyph: {} pixels across {} blocks raw={}x{} (bbox: {},{} -> {},{})",
                glyphArea, glyph.blockCount(),
                rawMatrix[0].length, rawMatrix.length,
                String.format("%.1f", glyph.minWorldX()), String.format("%.1f", glyph.minWorldY()),
                String.format("%.1f", glyph.maxWorldX()), String.format("%.1f", glyph.maxWorldY()));

        if (config.saveDebugImage() && Config.ENABLE_SYMBOL_MATCH_DEBUG_OUTPUT.get()) {
            saveDebugMatrixPng(rawMatrix);
        }

        List<SecretTextMatcher.Match> secretTextResults =
                SecretTextMatcher.INSTANCE.recognize(rawMatrix);
        if (!secretTextResults.isEmpty()) {
            LOGGER.debug("[SymbolRecognizer] Exact secret-text match: {}", secretTextResults);
            List<SymbolMatch> exactMatches = new ArrayList<>();
            for (SecretTextMatcher.Match result : secretTextResults) {
                exactMatches.add(toSymbolMatch(result.symbol(), 1.0f,
                        result.rotationDegrees(), glyph, maxDirectionX, maxDirectionY));
            }
            return new RecognitionResult(exactMatches, secretTextResults);
        }

        // Secret-text matching is deliberately a strict fast path. If it has
        // no usable result, preserve the original skeleton matching path and
        // pass the untouched raw matrix into it.
        Map<String, SymbolTemplate> tplIndex = new HashMap<>();
        for (SymbolTemplate t : symbolRegistry) tplIndex.put(t.id().toString(), t);

        SkeletonMatcher matcher = SkeletonMatcher.getInstance();
        List<SkeletonMatcher.Match> results = matcher.recognize(rawMatrix);

        List<SymbolMatch> matches = new ArrayList<>();
        for (SkeletonMatcher.Match result : results) {
            SymbolTemplate template = tplIndex.get(result.templateId().toString());
            if (template == null) continue;

            matches.add(toSymbolMatch(template, result.confidence(),
                    result.rotationDegrees(), glyph, maxDirectionX, maxDirectionY));
        }

        if (!matches.isEmpty()) {
            SymbolMatch best = matches.getFirst();
            LOGGER.debug("[SymbolRecognizer] >> BEST MATCH: {} (conf={}, role={})",
                    best.symbolId(), String.format("%.3f", best.confidence()), best.role());
        } else {
            LOGGER.debug("[SymbolRecognizer] >> NO MATCH - no template passed skeleton matcher thresholds");
        }

        return new RecognitionResult(matches, List.of());
    }

    private static SymbolMatch toSymbolMatch(SymbolTemplate template, float confidence,
                                             float rotationDegrees, ExtractedGlyph glyph,
                                             int maxDirectionX, int maxDirectionY) {
        return toSymbolMatch(template.id(), template.defaultRole(), confidence,
                rotationDegrees, glyph, maxDirectionX, maxDirectionY);
    }

    private static SymbolMatch toSymbolMatch(SecretTextSymbol symbol, float confidence,
                                             float rotationDegrees, ExtractedGlyph glyph,
                                             int maxDirectionX, int maxDirectionY) {
        return toSymbolMatch(symbol.id(), symbol.role(), confidence,
                rotationDegrees, glyph, maxDirectionX, maxDirectionY);
    }

    private static SymbolMatch toSymbolMatch(net.minecraft.resources.ResourceLocation symbolId,
                                             SymbolRole role,
                                             float confidence, float rotationDegrees,
                                             ExtractedGlyph glyph,
                                             int maxDirectionX, int maxDirectionY) {
        float centerX = (float) ((glyph.minWorldX() + glyph.maxWorldX()) / 2.0);
        float centerY = (float) ((glyph.minWorldY() + glyph.maxWorldY()) / 2.0);
        Pose pose = computePose(glyph, rotationDegrees, maxDirectionX, maxDirectionY);
        return new SymbolMatch(
                symbolId, confidence, rotationDegrees, false, 1f,
                pose.front(), pose.length(), pose.width(),
                centerX, centerY, role);
    }

    private record Pose(Vec3 front, double length, double width) {}

    private static Pose computePose(ExtractedGlyph glyph, float rotationDegrees,
                                    int maxDirectionX, int maxDirectionY) {
        PixelBasis basis = snapBasis(
                pixelBasis(rotationDegrees), maxDirectionX, maxDirectionY);
        Vec3 front = worldFront(glyph, basis.frontX(), basis.frontY());
        double[] size = projectedSize(glyph, basis.frontX(), basis.frontY());
        return new Pose(front, size[0], size[1]);
    }

    record PixelBasis(double frontX, double frontY) {}

    private static PixelBasis pixelBasis(float rotationDegrees) {
        double radians = Math.toRadians(rotationDegrees - 90.0);
        return new PixelBasis(Math.cos(radians), Math.sin(radians));
    }

    static PixelBasis snapBasis(PixelBasis basis, int maxDx, int maxDy) {
        double magnitude = Math.hypot(basis.frontX(), basis.frontY());
        if (magnitude < 1.0E-9 || (maxDx <= 0 && maxDy <= 0)) return basis;
        double unitX = basis.frontX() / magnitude;
        double unitY = basis.frontY() / magnitude;
        int bestDx = 0;
        int bestDy = 0;
        double bestDot = Double.NEGATIVE_INFINITY;
        for (int dx = -maxDx; dx <= maxDx; dx++) {
            for (int dy = -maxDy; dy <= maxDy; dy++) {
                if (dx == 0 && dy == 0) continue;
                double inverse = 1.0 / Math.hypot(dx, dy);
                double dot = (dx * unitX + dy * unitY) * inverse;
                if (dot > bestDot) {
                    bestDot = dot;
                    bestDx = dx;
                    bestDy = dy;
                }
            }
        }
        if (bestDx == 0 && bestDy == 0) return basis;
        double inverse = 1.0 / Math.hypot(bestDx, bestDy);
        return new PixelBasis(bestDx * inverse, bestDy * inverse);
    }

    private static Vec3 worldFront(ExtractedGlyph glyph, double fx, double fy) {
        Direction face = glyph.pixels().isEmpty()
                ? Direction.NORTH
                : glyph.pixels().iterator().next().face();
        Vec3 front = switch (face) {
            case NORTH, SOUTH -> new Vec3(fx, fy, 0);
            case EAST, WEST -> new Vec3(0, fy, fx);
            case UP, DOWN -> new Vec3(fx, 0, fy);
        };
        return front.lengthSqr() > 1e-12 ? front.normalize() : Vec3.ZERO;
    }

    private static double[] projectedSize(ExtractedGlyph glyph, double fx, double fy) {
        if (glyph.worldX().length == 0) return new double[]{0.0, 0.0};

        double rx = -fy;
        double ry = fx;
        double minFront = Double.POSITIVE_INFINITY;
        double maxFront = Double.NEGATIVE_INFINITY;
        double minRight = Double.POSITIVE_INFINITY;
        double maxRight = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < glyph.worldX().length; i++) {
            double x = glyph.worldX()[i];
            double y = glyph.worldY()[i];
            double along = x * fx + y * fy;
            double across = x * rx + y * ry;
            minFront = Math.min(minFront, along);
            maxFront = Math.max(maxFront, along);
            minRight = Math.min(minRight, across);
            maxRight = Math.max(maxRight, across);
        }
        return new double[]{maxFront - minFront, maxRight - minRight};
    }

    public static List<SymbolMatch> recognize(ExtractedGlyph glyph) {
        return recognize(glyph, GyromancyRegistries.SYMBOL, RecognizerConfig.DEFAULT);
    }

    public static RecognitionResult recognizeDetailed(ExtractedGlyph glyph) {
        return recognizeDetailed(glyph, GyromancyRegistries.SYMBOL, RecognizerConfig.DEFAULT);
    }

    public static List<SymbolMatch> recognize(ExtractedGlyph glyph, float confidenceThreshold) {
        return recognize(glyph, GyromancyRegistries.SYMBOL,
                new RecognizerConfig(confidenceThreshold));
    }

    static void saveDebugMatrixPng(int[][] matrix) {
        if (matrix.length == 0 || matrix[0].length == 0) return;

        File outDir = new File("build/skeleton_viz/raw_glyph");
        if (!outDir.exists() && !outDir.mkdirs()) return;

        int width = matrix[0].length;
        int height = matrix.length;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                img.setRGB(x, y, matrix[y][x] != 0 ? 0xFF000000 : 0x00000000);

        File out = new File(outDir, String.format("glyph_%05d_%dx%d.png",
                DEBUG_IMAGE_ID.incrementAndGet(), width, height));
        try {
            ImageIO.write(img, "PNG", out);
            LOGGER.debug("[SymbolRecognizer] Raw glyph matrix saved: {}", out.getPath());
        } catch (IOException e) {
            LOGGER.debug("[SymbolRecognizer] Failed to save raw glyph matrix PNG", e);
        }
    }
}
