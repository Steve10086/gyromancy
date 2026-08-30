package com.astune.gyromancy.symbol;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.symbol.ParameterRune;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Shared exact matcher for the 3×3 秘文 rune set.
 *
 * <p>All 秘文 definitions are keyed by {@link SecretText}, whose values own
 * independent {@link SecretTextSymbol} objects. The matcher only reads the
 * input matrix and therefore leaves it untouched on both success and failure.
 */
public final class SecretTextMatcher {

    public static final SecretTextMatcher INSTANCE = new SecretTextMatcher();

    private final Map<SecretText, int[][]> patterns = new EnumMap<>(SecretText.class);

    private SecretTextMatcher() {
        loadPatterns();
    }

    /**
     * Finds every 秘文 whose clipped pattern exactly matches the input in one
     * of the four clockwise quarter-turn orientations. The input may be any
     * non-empty rectangle no larger than 3×3.
     */
    public List<Match> recognize(int[][] image) {
        if (!isAtMostThreeByThree(image)) return List.of();

        List<Match> matches = new ArrayList<>();
        for (var entry : patterns.entrySet()) {
            ExactPixelMatcher.matchingRotation(image, entry.getValue())
                    .ifPresent(rotation -> matches.add(
                            new Match(entry.getKey().symbol(), rotation)));
        }
        return List.copyOf(matches);
    }

    /** Returns the loaded pattern count, primarily for diagnostics and tests. */
    int patternCount() {
        return patterns.size();
    }

    /**
     * Returns a defensive copy of the clipped pattern used by the matcher.
     * This lets the symbol registry expose the same pattern without sharing
     * mutable matrix storage with the matching path.
     */
    public int[][] patternFor(SecretText secretText) {
        int[][] pattern = patterns.get(secretText);
        if (pattern == null) return null;

        int[][] copy = new int[pattern.length][];
        for (int y = 0; y < pattern.length; y++) copy[y] = pattern[y].clone();
        return copy;
    }

    private void loadPatterns() {
        for (SecretText secretText : SecretText.values()) {
            int[][] pattern = loadPattern(secretText);
            if (pattern != null) patterns.put(secretText, pattern);
        }
    }

    private static int[][] loadPattern(SecretText secretText) {
        try (InputStream stream = SecretTextMatcher.class
                .getResourceAsStream(secretText.resourcePath())) {
            if (stream == null) {
                Gyromancy.LOGGER.warn("[SecretTextMatcher] Missing 秘文 resource: {}",
                        secretText.resourcePath());
                return null;
            }

            BufferedImage image = ImageIO.read(stream);
            if (image == null || image.getWidth() != SecretText.SIZE
                    || image.getHeight() != SecretText.SIZE) {
                Gyromancy.LOGGER.warn(
                        "[SecretTextMatcher] Ignoring {}: expected {}x{} PNG",
                        secretText.resourcePath(), SecretText.SIZE, SecretText.SIZE);
                return null;
            }

            int[][] pattern = new int[SecretText.SIZE][SecretText.SIZE];
            for (int y = 0; y < SecretText.SIZE; y++) {
                for (int x = 0; x < SecretText.SIZE; x++) {
                    int rgb = image.getRGB(x, y) & 0xFFFFFF;
                    pattern[y][x] = rgb < 0x202020 ? 1 : 0;
                }
            }
            return clipToForeground(pattern, secretText);
        } catch (IOException exception) {
            Gyromancy.LOGGER.warn("[SecretTextMatcher] Failed to load {}",
                    secretText.resourcePath(), exception);
            return null;
        }
    }

    private static int[][] clipToForeground(int[][] pattern, SecretText secretText) {
        int minX = SecretText.SIZE;
        int minY = SecretText.SIZE;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < pattern.length; y++) {
            for (int x = 0; x < pattern[y].length; x++) {
                if (pattern[y][x] == 0) continue;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }

        if (maxX < 0) {
            Gyromancy.LOGGER.warn("[SecretTextMatcher] Ignoring {}: no foreground pixels",
                    secretText.resourcePath());
            return null;
        }

        int[][] clipped = new int[maxY - minY + 1][maxX - minX + 1];
        for (int y = minY; y <= maxY; y++) {
            System.arraycopy(pattern[y], minX, clipped[y - minY], 0, clipped[y].length);
        }
        return clipped;
    }

    private static boolean isAtMostThreeByThree(int[][] image) {
        if (image == null || image.length < 1 || image.length > SecretText.SIZE
                || image[0] == null || image[0].length < 1
                || image[0].length > SecretText.SIZE) return false;
        for (int[] row : image) {
            if (row == null || row.length != image[0].length) return false;
        }
        return true;
    }

    public record Match(SecretTextSymbol symbol, float rotationDegrees) {
        public SecretText secretText() {
            return symbol.type();
        }

        public ParameterRune parameterRune() {
            return symbol.toParameterRune(1.0f);
        }
    }
}
