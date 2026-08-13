import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Queue;
import javax.imageio.ImageIO;

/** Crops only existing ImageGen artwork; it does not draw or synthesize pixels. */
public final class ExtractWandImageGenAssets {
    private static final int BACKGROUND_TOLERANCE = 34;

    private ExtractWandImageGenAssets() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("source outputDir");
        BufferedImage source = ImageIO.read(new File(args[0]));
        File output = new File(args[1]);
        if (!output.exists() && !output.mkdirs()) throw new IllegalStateException("Cannot create " + output);

        crop(source, output, "wand_panel.png", 236, 107, 446, 359, true);
        crop(source, output, "wand_surface.png", 300, 205, 320, 160, false);
        crop(source, output, "wand_header.png", 224, 23, 458, 59, true);
        crop(source, output, "wand_slot_left.png", 1081, 22, 255, 231, true);
        crop(source, output, "wand_slot_right.png", 1081, 22, 255, 231, true);
        crop(source, output, "wand_center_rail.png", 836, 22, 184, 814, true);
        crop(source, output, "wand_pin.png", 1390, 159, 108, 94, true);
    }

    private static void crop(BufferedImage source, File output, String name,
                             int x, int y, int width, int height,
                             boolean removeBackground) throws Exception {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int background = source.getRGB(x, y);
        for (int iy = 0; iy < height; iy++) {
            for (int ix = 0; ix < width; ix++) {
                target.setRGB(ix, iy, source.getRGB(x + ix, y + iy));
            }
        }
        if (removeBackground) removeConnectedBackground(target, background);
        ImageIO.write(target, "png", new File(output, name));
    }

    private static void removeConnectedBackground(BufferedImage image, int background) {
        boolean[][] visited = new boolean[image.getHeight()][image.getWidth()];
        Queue<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < image.getWidth(); x++) {
            queue.add(new int[]{x, 0});
            queue.add(new int[]{x, image.getHeight() - 1});
        }
        for (int y = 1; y < image.getHeight() - 1; y++) {
            queue.add(new int[]{0, y});
            queue.add(new int[]{image.getWidth() - 1, y});
        }
        while (!queue.isEmpty()) {
            int[] point = queue.remove();
            int x = point[0];
            int y = point[1];
            if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()
                    || visited[y][x]) continue;
            visited[y][x] = true;
            if (distance(image.getRGB(x, y), background) > BACKGROUND_TOLERANCE) continue;
            image.setRGB(x, y, 0x00000000);
            queue.add(new int[]{x - 1, y});
            queue.add(new int[]{x + 1, y});
            queue.add(new int[]{x, y - 1});
            queue.add(new int[]{x, y + 1});
        }
    }

    private static int distance(int first, int second) {
        int r = ((first >> 16) & 0xFF) - ((second >> 16) & 0xFF);
        int g = ((first >> 8) & 0xFF) - ((second >> 8) & 0xFF);
        int b = (first & 0xFF) - (second & 0xFF);
        return Math.abs(r) + Math.abs(g) + Math.abs(b);
    }
}
