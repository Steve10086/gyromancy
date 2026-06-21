package com.astune.gyromancy.symbol;

import com.astune.gyromancy.api.symbol.SymbolRole;
import com.astune.gyromancy.api.symbol.SymbolTemplate;
import com.astune.gyromancy.symbol.GeometricMatcher.MatchResult;
import com.astune.gyromancy.util.TemplateLoader;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end symbol matching test.
 *
 * <p>Rules (no exceptions):
 * <ol>
 *   <li>Image with prefix → prefix template score ≥ threshold AND ≥ all others</li>
 *   <li>Image without prefix (noise) → all template scores &lt; threshold</li>
 * </ol>
 */
class SymbolMatcherTest {

    private static final float THRESHOLD = 0.50f;

    private static final File TEST_SYMBOL_DIR = new File("src/test/resources/test_images/symbol");
    private static final File TEST_RUNE_DIR   = new File("src/test/resources/test_images/rune");
    private static final String TPL_SYMBOL_PATH = "/assets/gyromancy/textures/symbol/";
    private static final String TPL_RUNE_PATH   = "/assets/gyromancy/textures/rune/";

    // ═══════════════════════ SYMBOLS ═══════════════════════

    static Stream<String> testSymbolNames() {
        return pngNames(TEST_SYMBOL_DIR).stream();
    }

    @ParameterizedTest
    @MethodSource("testSymbolNames")
    @DisplayName("Symbol: prefix match > threshold and ≥ all others; noise: all < threshold")
    void testSymbol(String testName) {
        evaluate(testName, TPL_SYMBOL_PATH, TEST_SYMBOL_DIR,
                SymbolRole.CENTER_SYMBOL);
    }

    // ═══════════════════════ RUNES ═══════════════════════

    static Stream<String> testRuneNames() {
        return pngNames(TEST_RUNE_DIR).stream();
    }

    @ParameterizedTest
    @MethodSource("testRuneNames")
    @DisplayName("Rune: prefix match > threshold and ≥ all others; noise: all < threshold")
    void testRune(String testName) {
        evaluate(testName, TPL_RUNE_PATH, TEST_RUNE_DIR,
                SymbolRole.PARAMETER_RUNE);
    }

    // ═══════════════════════ unified evaluation ═══════════════════════

    private void evaluate(String testName, String tplPath, File testDir,
                          SymbolRole role) {
        int[][] testImg = TemplateLoader.load(
                "/test_images/" + testDir.getName() + "/" + testName + ".png");

        // Match against all templates, track best
        List<String> allNames = pngNames(tplPath);
        String bestName = "";
        float bestScore = 0f;
        Map<String, Float> allScores = new LinkedHashMap<>();

        for (String tplName : allNames) {
            SymbolTemplate tpl = loadTemplate(tplPath, tplName, role);
            float score = GeometricMatcher.match(testImg, tpl).confidence();
            allScores.put(tplName, score);
            if (score > bestScore) { bestScore = score; bestName = tplName; }
        }

        // Log
        StringBuilder sb = new StringBuilder(
                String.format("[MatcherTest] %-22s  threshold=%s%n", testName, fmt(THRESHOLD)));
        for (var e : allScores.entrySet()) {
            sb.append(String.format("  %-22s  conf=%s%n", e.getKey(), fmt(e.getValue())));
        }
        sb.append(String.format("  BEST = %s (%s)%n", bestName, fmt(bestScore)));
        System.out.print(sb);

        // Rule
        String prefix = findPrefixOrNull(testName, allNames);

        if (prefix == null) {
            // noise: all scores must be < threshold
            if (bestScore >= THRESHOLD) {
                fail("noise " + testName + " matched " + bestName
                        + " conf=" + fmt(bestScore) + " (threshold=" + fmt(THRESHOLD) + ")");
            }
        } else {
            // known shape: prefix score must be ≥ threshold AND ≥ all others
            float prefixScore = allScores.get(prefix);

            assertTrue(prefixScore >= THRESHOLD,
                    () -> String.format("%s prefix=%s self=%s < threshold=%s",
                            testName, prefix, fmt(prefixScore), fmt(THRESHOLD)));

            for (var e : allScores.entrySet()) {
                if (e.getKey().equals(prefix)) continue;
                assertTrue(prefixScore >= e.getValue(),
                        () -> String.format("%s prefix=%s self=%s < %s=%s",
                                testName, prefix, fmt(prefixScore),
                                e.getKey(), fmt(e.getValue())));
            }
        }
    }

    // ═══════════════════════ helpers ═══════════════════════

    private static SymbolTemplate loadTemplate(String tplPath, String name, SymbolRole role) {
        int[][] p = TemplateLoader.load(tplPath + name + ".png");
        boolean allowRot = name.equals("star") || name.equals("wind_symbol") || name.equals("rune_wind");
        return new SymbolTemplate(rl(name), p, 0, allowRot, false, role);
    }

    private static List<String> pngNames(File dir) {
        List<String> names = new ArrayList<>();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".png"));
        if (files != null) for (File f : files) names.add(noExt(f.getName()));
        names.sort(Comparator.comparingInt(String::length).reversed());
        return names;
    }

    private static List<String> pngNames(String classpathDir) {
        return pngNames(new File("src/main/resources" + classpathDir));
    }

    private static String noExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String findPrefixOrNull(String testName, List<String> candidates) {
        String best = "";
        for (String c : candidates)
            if (testName.startsWith(c) && c.length() > best.length()) best = c;
        return best.isEmpty() ? null : best;
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("gyromancy", path);
    }

    private static String fmt(float v) { return String.format("%.4f", v); }
}
