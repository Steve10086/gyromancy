package com.astune.gyromancy.util;

import com.astune.gyromancy.Gyromancy;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * Loads 32×32 PNG images and converts them to binary int[][] patterns.
 * Black pixels (RGB ≈ 0) → 1, everything else → 0.
 */
public final class TemplateLoader {

    private TemplateLoader() {}

    /**
     * Loads a 32×32 PNG from the classpath, converting to binary pattern.
     * @param path resource path, e.g. "/assets/gyromancy/textures/symbol/square.png"
     * @return int[32][32] binary pattern
     */
    public static int[][] load(String path) {
        try (InputStream is = TemplateLoader.class.getResourceAsStream(path)) {
            if (is == null) {
                Gyromancy.LOGGER.error("[TemplateLoader] Resource not found: {}", path);
                return new int[32][32];
            }
            BufferedImage img = ImageIO.read(is);
            if (img == null) {
                Gyromancy.LOGGER.error("[TemplateLoader] Failed to decode: {}", path);
                return new int[32][32];
            }
            return fromImage(img);
        } catch (IOException e) {
            Gyromancy.LOGGER.error("[TemplateLoader] Error loading: {}", path, e);
            return new int[32][32];
        }
    }

    private static int[][] fromImage(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[][] raw = new int[32][32];

        for (int y = 0; y < 32 && y < h; y++) {
            for (int x = 0; x < 32 && x < w; x++) {
                int rgb = img.getRGB(x, y) & 0xFFFFFF;
                // black → 1, non-black → 0
                raw[y][x] = (rgb < 0x202020) ? 1 : 0;
            }
        }
        // Apply same normalization as drawn glyphs (center + pad)
        return GeometryUtils.normalize(raw, 32, 32);
    }
}
