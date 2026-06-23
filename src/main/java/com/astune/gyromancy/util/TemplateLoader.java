package com.astune.gyromancy.util;

import com.astune.gyromancy.Gyromancy;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads 32×32 PNG images and converts them to binary int[][] patterns.
 * Black pixels (RGB &lt; 0x202020) → 1, everything else → 0.
 *
 * <p>Internally caches both raw (threshold-only) and normalized
 * (centered + padded via GeometryUtils.normalize) versions.
 */
public final class TemplateLoader {

    private TemplateLoader() {}

    /** Cached pair: raw matrix (thresholded) + normalized matrix. */
    private record ImagePair(int[][] raw, int[][] normalized) {}

    private static final ConcurrentHashMap<String, ImagePair> cache = new ConcurrentHashMap<>();

    // ═══════════════════════ Public API ═══════════════════════

    /**
     * Loads a 32×32 PNG from the classpath, thresholding only.
     * Returns the raw binary — no centering, padding, or scaling applied.
     *
     * @param path resource path, e.g. "/test_images/symbol/square_1.png"
     * @return int[32][32] raw binary (1=foreground pixel, 0=background)
     */
    public static int[][] loadRaw(String path) {
        return getPair(path).raw();
    }

    /**
     * Loads a 32×32 PNG from the classpath, normalized via
     * GeometryUtils.normalize() (center + 10% pad + 2×2 floor sampling).
     * This matches what the gameplay pipeline and geometric matcher expect.
     *
     * @param path resource path
     * @return int[32][32] normalized binary
     */
    public static int[][] loadNormalized(String path) {
        return getPair(path).normalized();
    }

    /**
     * Default load — returns raw binary (threshold only, no normalization).
     * The geometric matcher normalizes internally via GeometryUtils.normalize().
     * The ML classifier expects raw input to match training distribution.
     *
     * @param path resource path
     * @return int[32][32] raw binary
     */
    public static int[][] load(String path) {
        return loadRaw(path);
    }

    // ═══════════════════════ Internals ═══════════════════════

    private static ImagePair getPair(String path) {
        return cache.computeIfAbsent(path, TemplateLoader::loadPair);
    }

    private static ImagePair loadPair(String path) {
        try (InputStream is = TemplateLoader.class.getResourceAsStream(path)) {
            if (is == null) {
                Gyromancy.LOGGER.error("[TemplateLoader] Resource not found: {}", path);
                int[][] empty = new int[32][32];
                return new ImagePair(empty, empty);
            }
            BufferedImage img = ImageIO.read(is);
            if (img == null) {
                Gyromancy.LOGGER.error("[TemplateLoader] Failed to decode: {}", path);
                int[][] empty = new int[32][32];
                return new ImagePair(empty, empty);
            }
            int[][] raw = thresholdImage(img);
            int[][] norm = GeometryUtils.normalize(raw, 32, 32);
            return new ImagePair(raw, norm);
        } catch (IOException e) {
            Gyromancy.LOGGER.error("[TemplateLoader] Error loading: {}", path, e);
            int[][] empty = new int[32][32];
            return new ImagePair(empty, empty);
        }
    }

    private static int[][] thresholdImage(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[][] raw = new int[32][32];
        for (int y = 0; y < 32 && y < h; y++) {
            for (int x = 0; x < 32 && x < w; x++) {
                int rgb = img.getRGB(x, y) & 0xFFFFFF;
                raw[y][x] = (rgb < 0x202020) ? 1 : 0;
            }
        }
        return raw;
    }
}
