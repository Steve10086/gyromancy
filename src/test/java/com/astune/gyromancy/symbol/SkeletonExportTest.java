package com.astune.gyromancy.symbol;

import com.astune.gyromancy.util.GeometryUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * Pure Java pipeline (no Python):
 *   PNG → fillSmallHoles → upscale(≥128, connectivity-preserving)
 *       → gaussianSmoothBinary(σ=K/3) → skeletonize()
 *
 * <p>Output per image (in {@code build/skeleton_viz/}):
 *   {@code {name}_smooth.png} — upscaled + smoothed
 *   {@code {name}_skel.png}   — Guo-Hall skeleton (skimage-equivalent)
 */
public class SkeletonExportTest {

    @Test public void exportAllSkeletons() throws IOException { run(); }
    public static void main(String[] args) throws IOException { run(); }

    private static void run() throws IOException {
        File outDir = new File("build/skeleton_viz");
        if (outDir.exists()) {
            File[] old = outDir.listFiles();
            if (old != null) for (File f : old) f.delete();
        }
        outDir.mkdirs();

        int count = 0;
        count += scanDir(new File("src/test/resources/test_images/symbol"), outDir);
        count += scanDir(new File("src/test/resources/test_images/rune"), outDir);
        System.out.println("[SkeletonExport] Done. " + count + " pairs in " + outDir.getAbsolutePath());
    }

    private static int scanDir(File dir, File outDir) {
        File[] files = dir.listFiles((d, n) -> n.endsWith(".png"));
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

            int[][] croppedSkel = GeometryUtils.cropToForeground(skel);
            int[][] croppedSmooth = GeometryUtils.cropToForeground(smooth);

            savePng(smooth, new File(outDir, name + "_smooth.png"));
            savePng(skel, new File(outDir, name + "_skel.png"));

            File cropDir = new File(outDir, "cropped");
            cropDir.mkdirs();
            savePng(croppedSmooth, new File(cropDir, name + "_smooth.png"));
            savePng(croppedSkel, new File(cropDir, name + "_skel.png"));

            System.out.printf("[SkeletonExport] %-28s %d×%d → %d×%d → skel %d×%d%n",
                    name, raw[0].length, raw.length,
                    smooth[0].length, smooth.length,
                    croppedSkel[0].length, croppedSkel.length);
            count++;
        }
        return count;
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
        } catch (IOException e) { return null; }
    }

    private static int countFg(int[][] img) { int c=0; for(int[] r:img) for(int v:r) if(v!=0) c++; return c; }

    private static void savePng(int[][] pixels, File out) {
        int h = pixels.length, w = h > 0 ? pixels[0].length : 0;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                img.setRGB(x, y, pixels[y][x] != 0 ? 0x000000 : 0xFFFFFF);
        try { ImageIO.write(img, "PNG", out); }
        catch (IOException e) { System.err.println("[SkeletonExport] FAIL " + out); }
    }
}
