package com.astune.gyromancy.symbol;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.SymbolMatch;
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
import java.util.Collections;
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
 *   <li>Match against all registered templates via {@link SkeletonMatcher}</li>
 *   <li>Keep matches returned by {@link SkeletonMatcher}'s hard/soft threshold pipeline</li>
 *   <li>Return the resulting {@link SymbolMatch}(es)</li>
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
        /** Kept for API compatibility; SkeletonMatcher now owns all match thresholds. */
        public static final RecognizerConfig DEFAULT = new RecognizerConfig(0.40f, true);
        public static final RecognizerConfig CLIENT_PREVIEW = new RecognizerConfig(0.40f, false);

        public RecognizerConfig(float confidenceThreshold) {
            this(confidenceThreshold, true);
        }
    }

    /**
     * Recognizes an extracted glyph by rasterizing and matching against all templates.
     */
    public static List<SymbolMatch> recognize(
            ExtractedGlyph glyph,
            Registry<SymbolTemplate> symbolRegistry,
            RecognizerConfig config) {

        if (glyph.pixels().isEmpty()) {
            LOGGER.debug("[SymbolRecognizer] Empty glyph - no pixels to recognize");
            return Collections.emptyList();
        }

        int[][] rawMatrix = FloodFillExtractor.rawGlyphMatrix(glyph);
        int glyphArea = glyph.pixels().size();

        LOGGER.debug("[SymbolRecognizer] Matching glyph: {} pixels across {} blocks raw={}x{} (bbox: {},{} -> {},{})",
                glyphArea, glyph.blockCount(),
                rawMatrix[0].length, rawMatrix.length,
                String.format("%.1f", glyph.minWorldX()), String.format("%.1f", glyph.minWorldY()),
                String.format("%.1f", glyph.maxWorldX()), String.format("%.1f", glyph.maxWorldY()));

        if (config.saveDebugImage()) saveDebugMatrixPng(rawMatrix);

        SkeletonMatcher matcher = SkeletonMatcher.getInstance();
        List<SkeletonMatcher.Match> results = matcher.recognize(rawMatrix);

        Map<String, SymbolTemplate> tplIndex = new HashMap<>();
        for (SymbolTemplate t : symbolRegistry) tplIndex.put(t.id().toString(), t);

        List<SymbolMatch> matches = new ArrayList<>();
        for (SkeletonMatcher.Match result : results) {
            SymbolTemplate template = tplIndex.get(result.templateId().toString());
            if (template == null) continue;

            float centerX = (float) ((glyph.minWorldX() + glyph.maxWorldX()) / 2.0);
            float centerY = (float) ((glyph.minWorldY() + glyph.maxWorldY()) / 2.0);
            Pose pose = computePose(glyph, result.rotationDegrees());

            matches.add(new SymbolMatch(
                    template.id(),
                    result.confidence(),
                    result.rotationDegrees(), false, 1f,
                    pose.front(), pose.length(), pose.width(),
                    centerX, centerY,
                    template.defaultRole()
            ));
        }

        if (!matches.isEmpty()) {
            SymbolMatch best = matches.getFirst();
            LOGGER.debug("[SymbolRecognizer] >> BEST MATCH: {} (conf={}, role={})",
                    best.symbolId(), String.format("%.3f", best.confidence()), best.role());
        } else {
            LOGGER.debug("[SymbolRecognizer] >> NO MATCH - no template passed skeleton matcher thresholds");
        }

        return matches;
    }

    private record Pose(Vec3 front, double length, double width) {}

    private static Pose computePose(ExtractedGlyph glyph, float rotationDegrees) {
        PixelBasis basis = pixelBasis(rotationDegrees);
        Vec3 front = worldFront(glyph, basis.frontX(), basis.frontY());
        double[] size = projectedSize(glyph, basis.frontX(), basis.frontY());
        return new Pose(front, size[0], size[1]);
    }

    private record PixelBasis(double frontX, double frontY) {}

    private static PixelBasis pixelBasis(float rotationDegrees) {
        double radians = Math.toRadians(rotationDegrees - 90.0);
        return new PixelBasis(Math.cos(radians), Math.sin(radians));
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
