package com.astune.gyromancy.symbol;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Debug export for the production skeleton matcher.
 *
 * <p>Output: {@code build/skeleton_viz/hard_match/{testName}.txt}
 */
public class SymbolMatcherTest {

    @Test
    public void matchAll() throws IOException {
        run();
    }

    public static void main(String[] args) throws IOException {
        run();
    }

    private static void run() throws IOException {
        File outDir = new File("build/skeleton_viz/hard_match");
        if (outDir.exists()) {
            File[] old = outDir.listFiles();
            if (old != null) for (File f : old) f.delete();
        }
        outDir.mkdirs();

        File symbolTplDir = new File("src/main/resources/assets/gyromancy/textures/symbol");
        File symbolTestDir = new File("src/test/resources/test_images/symbol");

        int count = matchDir(symbolTestDir, symbolTplDir, outDir);
        System.out.println("[SymbolMatcher] Done. " + count + " test cases -> " + outDir.getAbsolutePath());
    }

    private static int matchDir(File testDir, File tplDir, File outDir) {
        File[] testFiles = testDir.listFiles((d, n) -> n.endsWith(".png"));
        File[] tplFiles = tplDir.listFiles((d, n) -> n.endsWith(".png"));
        if (testFiles == null || tplFiles == null) return 0;

        Map<String, SkeletonMatcher.SkeletonStats> tplStats = new LinkedHashMap<>();
        for (File f : tplFiles) {
            int[][] raw = loadPng(f);
            if (raw == null) continue;
            SkeletonMatcher.SkeletonStats stats = SkeletonMatcher.computeStats(raw);
            if (stats != null) tplStats.put(noExt(f.getName()), stats);
        }

        int count = 0;
        for (File testFile : testFiles) {
            String testName = noExt(testFile.getName());
            int[][] raw = loadPng(testFile);
            if (raw == null) continue;

            SkeletonMatcher.SkeletonStats testStats = SkeletonMatcher.computeStats(raw);
            if (testStats == null) continue;

            Map<String, double[]> passed = new LinkedHashMap<>();
            Map<String, double[]> softRejected = new LinkedHashMap<>();

            for (var entry : tplStats.entrySet()) {
                SkeletonMatcher.SkeletonStats tpl = entry.getValue();
                if (!SkeletonMatcher.passesHardLayers(tpl, testStats)) continue;

                SkeletonMatcher.MatchScore score = SkeletonMatcher.minGraphEditMatch(tpl, testStats);
                double[] scores = toDebugScores(score);
                SkeletonMatcher.SoftThresholds thresholds = SymbolCatalog.thresholdsFor(entry.getKey());
                if (SkeletonMatcher.passesSoftThresholds(thresholds, score)) {
                    passed.put(entry.getKey(), scores);
                } else {
                    softRejected.put(entry.getKey(), scores);
                }
            }

            writeResult(new File(outDir, testName + ".txt"),
                    testName, testStats, tplStats, passed, softRejected);
            count++;
        }
        return count;
    }

    private static double[] toDebugScores(SkeletonMatcher.MatchScore score) {
        return new double[] {
                score.combined(), score.segment(), score.length(),
                score.turning(), score.endpointAngle(), score.edit()
        };
    }

    private static void writeResult(File out, String testName,
                                    SkeletonMatcher.SkeletonStats testStats,
                                    Map<String, SkeletonMatcher.SkeletonStats> tplStats,
                                    Map<String, double[]> passed,
                                    Map<String, double[]> softRejected) {
        StringBuilder sb = new StringBuilder();
        sb.append("Test: ").append(testName).append('\n');
        sb.append(String.format("  openLines=%d (outer=%d inner=%d)  closedLoops=%d  (nodes=%d  edges=%d)%n",
                testStats.openLines(), testStats.outerOpenLines(), testStats.innerOpenLines(),
                testStats.closedLoops(), testStats.nodes(), testStats.edges()));
        sb.append("  Pre-split junction degrees:");
        appendJunctionDegrees(sb, testStats);
        sb.append('\n');
        sb.append("  Pre-split edges:");
        appendEdgeTypes(sb, testStats);
        sb.append("\n");

        sb.append("\nTemplates:\n");
        for (var entry : tplStats.entrySet()) {
            SkeletonMatcher.SkeletonStats s = entry.getValue();
            sb.append(String.format("  %-22s  openLines=%d(outer=%d inner=%d)  closedLoops=%d  (nodes=%d  edges=%d)%n",
                    entry.getKey(), s.openLines(), s.outerOpenLines(), s.innerOpenLines(),
                    s.closedLoops(), s.nodes(), s.edges()));
            sb.append("    junction degs:");
            appendJunctionDegrees(sb, s);
            sb.append('\n');
        }

        SkeletonMatcher.SoftThresholds defaults = SkeletonMatcher.DEFAULT_THRESHOLDS;
        SkeletonMatcher.SoftThresholds arrow = SymbolCatalog.thresholdsFor("arrow");
        SkeletonMatcher.SoftThresholds circleOuter = SymbolCatalog.thresholdsFor("circle_outer");
        sb.append(String.format("%nDefault soft thresholds: segment>=%.2f  length>=%.2f  turn>=%.2f  angle>=%.2f  edit>=%.2f%n",
                defaults.segment(), defaults.length(), defaults.turning(),
                defaults.endpointAngle(), defaults.edit()));
        sb.append(String.format("Special thresholds: arrow length>=%.2f edit>=%.2f; circle_outer all>=%.2f%n",
                arrow.length(), arrow.edit(), circleOuter.segment()));

        sb.append("\nPassed hard layers + soft thresholds:\n");
        appendScores(sb, passed, false);

        sb.append("\nRejected by soft thresholds:\n");
        appendScores(sb, softRejected, true);

        try (FileWriter fw = new FileWriter(out)) {
            fw.write(sb.toString());
        } catch (IOException ignored) {
        }
    }

    private static void appendJunctionDegrees(StringBuilder sb, SkeletonMatcher.SkeletonStats stats) {
        for (var node : stats.preNodes())
            if (!node.isEndpoint() && node.degree() != 0) sb.append(' ').append(node.degree());
    }

    private static void appendEdgeTypes(StringBuilder sb, SkeletonMatcher.SkeletonStats stats) {
        for (var edge : stats.preEdges()) {
            boolean aEndpoint = nodeById(stats, edge.from()).isEndpoint();
            boolean bEndpoint = nodeById(stats, edge.to()).isEndpoint();
            if (aEndpoint && bEndpoint) sb.append(" [EE]");
            else if (aEndpoint) sb.append(" [EJ]");
            else if (bEndpoint) sb.append(" [JE]");
            else sb.append(" [JJ]");
        }
    }

    private static com.astune.gyromancy.util.GeometryPreprocessUtils.SkelNode nodeById(
            SkeletonMatcher.SkeletonStats stats, int id) {
        for (var node : stats.preNodes())
            if (node.id() == id) return node;
        throw new IllegalArgumentException("Missing node id " + id);
    }

    private static void appendScores(StringBuilder sb, Map<String, double[]> scores, boolean includeFailed) {
        if (scores.isEmpty()) {
            sb.append("  (none)\n");
            return;
        }

        sb.append(String.format("  %-22s  %8s  %7s  %7s  %7s  %7s  %7s",
                "name", "combined", "segment", "length", "turn", "angle", "edit"));
        if (includeFailed) sb.append("  failed");
        sb.append('\n');

        var sorted = new ArrayList<>(scores.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]));
        for (var entry : sorted) {
            double[] s = entry.getValue();
            sb.append(String.format("  %-22s  %8.4f  %7.4f  %7.4f  %7.4f  %7.4f  %7.4f",
                    entry.getKey(), s[0], s[1], s[2], s[3], s[4], s[5]));
            if (includeFailed) sb.append("  ").append(failedSoftMetrics(entry.getKey(), s));
            sb.append('\n');
        }
    }

    private static String failedSoftMetrics(String label, double[] s) {
        SkeletonMatcher.SoftThresholds t = SymbolCatalog.thresholdsFor(label);
        List<String> failed = new ArrayList<>();
        if (s[1] < t.segment()) failed.add("segment");
        if (s[2] < t.length()) failed.add("length");
        if (s[3] < t.turning()) failed.add("turn");
        if (s[4] < t.endpointAngle()) failed.add("angle");
        if (s[5] < t.edit()) failed.add("edit");
        return String.join(",", failed);
    }

    private static int[][] loadPng(File f) {
        try {
            BufferedImage img = ImageIO.read(f);
            if (img == null) return null;
            int w = img.getWidth(), h = img.getHeight();
            int[][] raw = new int[h][w];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    raw[y][x] = ((img.getRGB(x, y) & 0xFFFFFF) < 0x202020) ? 1 : 0;
            return raw;
        } catch (IOException e) {
            return null;
        }
    }

    private static String noExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
