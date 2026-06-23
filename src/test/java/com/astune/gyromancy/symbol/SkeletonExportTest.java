package com.astune.gyromancy.symbol;

import com.astune.gyromancy.util.GeometryUtils;
import com.astune.gyromancy.util.GeometryUtils.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

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

            int[][] filled = GeometryUtils.fillSmallHoles(raw);
            int minDim = Math.min(filled.length, filled[0].length);
            int K = (128 + minDim - 1) / minDim;

            int[][] up = GeometryUtils.upscaleConnectivityPreserving(filled, 128);
            int[][] smooth = GeometryUtils.gaussianSmoothBinary(up, K / 3.0);
            int[][] skel = GeometryUtils.skeletonize(smooth);
            int[][] cropped = GeometryUtils.cropToForeground(skel);

            List<SkelNode> nodes = new java.util.ArrayList<>(GeometryUtils.extractNodes(cropped));
            List<SkelEdge> edges = new java.util.ArrayList<>(GeometryUtils.traceEdges(cropped, nodes));
            GeometryUtils.mergeZeroEdges(nodes, edges);
            int minBranch = Math.max(cropped[0].length, cropped.length) / 10;
            GeometryUtils.pruneShortBranches(nodes, edges, minBranch);

            renderCombined(cropped, nodes, edges, new File(outDir, name + ".png"));

            System.out.printf("[SkeletonExport] %-28s %d×%d → skel %d×%d  nodes=%d  edges=%d%n",
                    name, raw[0].length, raw.length, cropped[0].length, cropped.length,
                    nodes.size(), edges.size());
            count++;
        }
        return count;
    }

    // ═══════════════════ Combined render: skeleton + graph ═══════════════════

    private static final Color SKEL_GRAY   = new Color(180, 180, 180);
    private static final Color EDGE_LINE   = new Color(60, 180, 60);

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

        // Green straight lines between connected nodes (no node markers)
        g.setColor(EDGE_LINE);
        g.setStroke(new BasicStroke(Math.max(1.5f, scale / 3f)));
        for (SkelEdge e : edges) {
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
}
