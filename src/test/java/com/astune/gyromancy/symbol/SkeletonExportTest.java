package com.astune.gyromancy.symbol;

import com.astune.gyromancy.util.GeometryUtils;
import com.astune.gyromancy.util.GeometryPreprocessUtils;
import com.astune.gyromancy.util.GeometryPreprocessUtils.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Pipeline:
 *   PNG → fillSmallHoles → upscale → smooth → skeletonize
 *       → cropToForeground → extractNodes → traceEdges
 *       → pruneShortBranches(max(w,h)/10)
 *       → render skeleton + graph overlay
 *
 * <p>Output: {@code build/skeleton_viz/{name}.png} — skeleton (gray) + graph overlay
 */
public class SkeletonExportTest {

    @Test public void exportAllSkeletons() throws IOException { run(); }
    public static void main(String[] args) throws IOException { run(); }

    private static void run() throws IOException {
        File outDir = new File("build/skeleton_viz");
        if (outDir.exists()) {
            File[] old = outDir.listFiles();
            if (old != null) for (File f : old) deleteRecursive(f);
        }
        outDir.mkdirs();

        int count = 0;
        count += processDir(new File("src/test/resources/test_images/symbol"), outDir);
        count += processDir(new File("src/test/resources/test_images/rune"), outDir);
        System.out.println("[SkeletonExport] Done. " + count + " images in " + outDir.getAbsolutePath());
    }

    private static int processDir(File srcDir, File outDir) {
        File[] files = srcDir.listFiles((d, n) -> n.endsWith(".png"));
        if (files == null) return 0;
        int count = 0;
        for (File f : files) {
            String name = f.getName().replace(".png", "");
            int[][] raw = loadPng(f);
            if (raw == null || raw.length == 0) continue;

            int[][] filled = GeometryPreprocessUtils.fillSmallHoles(raw);
            int minDim = Math.min(filled.length, filled[0].length);
            int K = (128 + minDim - 1) / minDim;

            int[][] up = GeometryPreprocessUtils.upscaleConnectivityPreserving(filled, 128);
            int[][] smooth = GeometryPreprocessUtils.gaussianSmoothBinary(up, K / 3.0);
            int[][] skel = GeometryPreprocessUtils.skeletonize(smooth);
            int[][] cropped = GeometryUtils.cropToForeground(skel);

            List<SkelNode> nodes = new java.util.ArrayList<>(GeometryPreprocessUtils.extractNodes(cropped));
            List<SkelEdge> edges = new java.util.ArrayList<>(GeometryPreprocessUtils.traceEdges(cropped, nodes));
            GeometryPreprocessUtils.mergeZeroEdges(nodes, edges);
            int minBranch = Math.max(cropped[0].length, cropped.length) / 10;
            GeometryPreprocessUtils.pruneShortBranches(nodes, edges, minBranch);
            GeometryPreprocessUtils.splitEdgesAtSupportPoints(nodes, edges);

            renderCombined(cropped, nodes, edges, new File(outDir, name + ".png"));
            writeDebug(name, nodes, edges, outDir);

            System.out.printf("[SkeletonExport] %-28s %d×%d → skel %d×%d  nodes=%d  edges=%d%n",
                    name, raw[0].length, raw.length, cropped[0].length, cropped.length,
                    nodes.size(), edges.size());
            count++;
        }
        return count;
    }

    // ═══════════════════ Combined render: skeleton + graph ═══════════════════

    private static final Color SKEL_GRAY   = new Color(180, 180, 180);
    private static final Color[] EDGE_COLORS = {
        new Color(220, 30, 30),   // red
        new Color(30, 180, 30),   // green
        new Color(30, 80, 220),   // blue
        new Color(220, 180, 30),  // yellow
    };

    private static void renderCombined(int[][] skel, List<SkelNode> nodes,
            List<SkelEdge> edges, File out) {
        int w = skel[0].length, h = skel.length;
        int scale = Math.max(1, Math.min(8, 400 / Math.max(w, h)));
        int iw = w * scale, ih = h * scale;
        BufferedImage img = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, iw, ih);

        // Base layer: skeleton pixels in light gray
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (skel[y][x] != 0) {
                    g.setColor(SKEL_GRAY);
                    g.fillRect(x * scale, y * scale, scale, scale);
                }

        // Straight lines, cycling colors for parallel edges (same node pair)
        Map<String, Integer> pairCount = new java.util.LinkedHashMap<>();
        for (SkelEdge e : edges) {
            String key = Math.min(e.from(), e.to()) + "_" + Math.max(e.from(), e.to());
            pairCount.merge(key, 1, Integer::sum);
        }
        Map<String, Integer> pairIdx = new java.util.HashMap<>();
        g.setStroke(new BasicStroke(Math.max(1.5f, scale / 3f)));
        for (SkelEdge e : edges) {
            String key = Math.min(e.from(), e.to()) + "_" + Math.max(e.from(), e.to());
            int idx = pairIdx.merge(key, 1, Integer::sum) - 1;
            g.setColor(EDGE_COLORS[idx % EDGE_COLORS.length]);
            SkelNode a = nodes.get(e.from()), b = nodes.get(e.to());
            g.drawLine(a.x() * scale + scale / 2, a.y() * scale + scale / 2,
                       b.x() * scale + scale / 2, b.y() * scale + scale / 2);
        }

        g.dispose();
        try { ImageIO.write(img, "PNG", out); }
        catch (IOException e) { System.err.println("[SkeletonExport] FAIL " + out); }
    }

    // ═══════════════════ Helpers ═══════════════════

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
        } catch (IOException e) { return null; }
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) { File[] kids = f.listFiles(); if (kids != null) for (File c : kids) deleteRecursive(c); }
        f.delete();
    }

    private static void writeDebug(String name, List<SkelNode> nodes, List<SkelEdge> edges, File outDir) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("  nodes=").append(nodes.size()).append("  edges=").append(edges.size()).append("\n\n");
        sb.append("Nodes:\n");
        for (SkelNode nd : nodes)
            sb.append("  [").append(nd.id()).append("] (").append(nd.x()).append(",").append(nd.y())
              .append(") deg=").append(nd.degree()).append("  ")
              .append(nd.isEndpoint() ? "ENDPOINT" : "JUNCTION").append("\n");
        sb.append("\nEdges:\n");
        for (int i = 0; i < edges.size(); i++) {
            SkelEdge e = edges.get(i);
            sb.append("  [").append(i).append("] ").append(e.from()).append("→").append(e.to())
              .append("  len=").append(e.length()).append("\n");
        }
        try {
            java.io.FileWriter fw = new java.io.FileWriter(new File(outDir, name + ".txt"), false);
            fw.write(sb.toString());
            fw.close();
        } catch (IOException ignored) {}
    }
}
