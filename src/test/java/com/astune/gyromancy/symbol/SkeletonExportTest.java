package com.astune.gyromancy.symbol;

import com.astune.gyromancy.util.GeometryUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * Diagnostic: upsamples all test images at their <b>native</b> resolution
 * using {@link GeometryUtils#upscaleConnectivityPreserving} and saves the
 * upscaled binary matrices as PNGs.
 *
 * <p>Output: {@code build/skeleton_viz/{name}.png}
 *
 * <p>Note: reads PNGs directly via ImageIO to preserve native dimensions.
 * TemplateLoader truncates everything to 32×32.
 */
public class SkeletonExportTest {

    @Test
    public void exportAllUpscaled() throws IOException {
        run();
    }

    public static void main(String[] args) throws IOException {
        run();
    }

    private static void run() throws IOException {
        File outDir = new File("build/skeleton_viz");
        outDir.mkdirs();

        scanDir(new File("src/test/resources/test_images/symbol"), outDir);
        scanDir(new File("src/test/resources/test_images/rune"), outDir);

        System.out.println("[SkeletonExport] Done. PNGs in " + outDir.getAbsolutePath());
    }

    private static void scanDir(File dir, File outDir) {
        File[] files = dir.listFiles((d, n) -> n.endsWith(".png"));
        if (files == null) return;
        for (File f : files) {
            String name = f.getName().replace(".png", "");

            int[][] raw = loadPngNative(f);
            if (raw == null || raw.length == 0) {
                System.err.println("[SkeletonExport] SKIP " + f + " — failed to load");
                continue;
            }

            int[][] upscaled = GeometryUtils.upscaleConnectivityPreserving(raw, 128);
            int minDim = Math.min(raw.length, raw[0].length);
            int K = (128 + minDim - 1) / minDim;
            double sigma = K / 3.0; // scale-adaptive: wider blocks need more blur
            int[][] smoothed = GeometryUtils.gaussianSmoothBinary(upscaled, sigma);
            savePng(upscaled, new File(outDir, name + "_raw.png"));
            savePng(smoothed, new File(outDir, name + "_smooth.png"));
            System.out.println("[SkeletonExport] " + name + ": "
                    + upscaled[0].length + "×" + upscaled.length
                    + " K=" + K + " σ=" + String.format("%.2f", sigma));
        }
    }

    /** Loads a PNG at its native resolution. Black→1, non-black→0. */
    private static int[][] loadPngNative(File file) {
        try {
            BufferedImage img = ImageIO.read(file);
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

    private static void savePng(int[][] pixels, File out) {
        int h = pixels.length, w = h > 0 ? pixels[0].length : 0;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                img.setRGB(x, y, pixels[y][x] != 0 ? 0x000000 : 0xFFFFFF);
        try {
            ImageIO.write(img, "PNG", out);
            System.out.println("[SkeletonExport] " + out.getName() + ": " + w + "×" + h);
        } catch (IOException e) {
            System.err.println("[SkeletonExport] FAILED to write " + out + ": " + e.getMessage());
        }
    }
}
