import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * One-shot generator: creates 32×32 PNG templates in src/main/resources.
 * Black=foreground, white=background.
 */
public class PngGenerator {

    static final String OUT = "src/main/resources/assets/gyromancy/textures";

    public static void main(String[] args) throws Exception {
        new File(OUT + "/symbol").mkdirs();
        new File(OUT + "/rune").mkdirs();

        save("symbol/circle_outer", circleOuter());
        save("symbol/square", square());
        save("symbol/triangle", triangle());
        save("symbol/star", star());
        save("symbol/figure_8", figure8());
        save("symbol/fire_symbol", fire());
        save("symbol/water_symbol", water());
        save("symbol/earth_symbol", earth());
        save("symbol/wind_symbol", wind());

        save("rune/magnitude_1", magnitude(1));
        save("rune/magnitude_2", magnitude(2));
        save("rune/magnitude_3", magnitude(3));
        save("rune/magnitude_4", magnitude(4));
        save("rune/magnitude_5", magnitude(5));
        save("rune/rune_fire", smallTriangle());
        save("rune/rune_water", wavyLine());
        save("rune/rune_earth", smallSquare());
        save("rune/rune_wind", smallSpiral());

        System.out.println("Done — " + new File(OUT).listFiles().length + " files.");
    }

    static void save(String path, int[][] p) throws Exception {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 32; x++)
                img.setRGB(x, y, p[y][x] != 0 ? 0x000000 : 0xFFFFFF);
        File f = new File(OUT, path + ".png");
        ImageIO.write(img, "png", f);
        System.out.println("  " + f);
    }

    // ═══════════════ patterns ═══════════════

    static int[][] ring(int outer, int inner) {
        int[][] p = new int[32][32];
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 32; x++) {
                double d = Math.sqrt((x - 15.5) * (x - 15.5) + (y - 15.5) * (y - 15.5));
                if (d >= inner && d <= outer) p[y][x] = 1;
            }
        return p;
    }

    static int[][] circleOuter() { return ring(14, 10); }

    static int[][] square() {
        int[][] p = new int[32][32];
        int m = 6;
        // hollow: just the border
        for (int x = m; x < 32 - m; x++) { p[m][x] = 1; p[31 - m][x] = 1; }
        for (int y = m; y < 32 - m; y++) { p[y][m] = 1; p[y][31 - m] = 1; }
        return p;
    }

    static int[][] triangle() {
        int[][] p = new int[32][32];
        int cx = 15, top = 5, bot = 26;
        // hollow: just the outline
        for (int y = top; y <= bot; y++) {
            int hw = (int)((bot - y) * 13.0 / (bot - top));
            p[y][cx - hw] = 1;
            p[y][cx + hw] = 1;
        }
        for (int x = cx - 13; x <= cx + 13; x++) p[bot][x] = 1;
        return p;
    }

    static int[][] star() {
        int[][] p = new int[32][32];
        double cx = 15.5, cy = 15.5, outer = 12.5, inner = 5.0;
        // hollow: just the boundary pixels
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                double dx = x - cx, dy = y - cy;
                double dist = Math.sqrt(dx * dx + dy * dy);
                double angle = Math.atan2(dy, dx) - Math.PI / 2;
                if (angle < 0) angle += 2 * Math.PI;
                double seg = angle % (2 * Math.PI / 5);
                double t = seg / (2 * Math.PI / 5);
                double r = (t <= 0.5) ? inner + (outer - inner) * (1 - 2 * t)
                                     : inner + (outer - inner) * (2 * t - 1);
                if (Math.abs(dist - r) < 1.2) p[y][x] = 1;
            }
        }
        return p;
    }

    static int[][] figure8() {
        int[][] p = new int[32][32];
        double cx = 15.5, r = 8.5;
        // two hollow circles + bridge
        double[] cy = {10.0, 21.0};
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                double dx = x - cx;
                for (double c : cy) {
                    double d = Math.sqrt(dx * dx + (y - c) * (y - c));
                    if (Math.abs(d - r) < 1.0) { p[y][x] = 1; break; }
                }
                // bridge
                if (Math.abs(x - 16) <= 2 && y >= 14 && y <= 17) p[y][x] = 1;
            }
        }
        return p;
    }

    static int[][] fire() { return triangle(); }  // same shape
    static int[][] water() {
        int[][] p = new int[32][32];
        // hollow teardrop
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                double dx = x - 15.5, dy = y - 10.0, r = 8;
                double d = Math.sqrt(dx * dx + dy * dy);
                if (Math.abs(d - r) < 0.9 && (dy > -r || Math.abs(x - 15.5) < 6)) p[y][x] = 1;
            }
        }
        // bottom taper
        for (int y = 18; y < 28; y++) {
            int hw = (28 - y) * 3 / 10 + 2;
            p[y][15 - hw] = p[y][16 + hw] = 1;
        }
        return p;
    }
    static int[][] earth() { return square(); }  // same shape
    static int[][] wind() {
        // hollow 3-arm spiral
        int[][] p = new int[32][32];
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                double dx = x - 15.5, dy = y - 15.5;
                double dist = Math.sqrt(dx * dx + dy * dy);
                double angle = Math.atan2(dy, dx);
                double target = dist / 14.0 * Math.PI * 2.5 + angle;
                if (Math.abs(Math.sin(target * 3)) < 0.25 && dist > 2 && dist < 14) p[y][x] = 1;
            }
        }
        return p;
    }

    static int[][] magnitude(int n) {
        int[][] p = new int[32][32];
        int totalH = n * 6 - 3;
        int startY = (32 - totalH) / 2;
        for (int i = 0; i < n; i++) {
            int by = startY + i * 6;
            for (int x = 6; x <= 25; x++) {
                p[by][x] = 1;
                p[by + 2][x] = 1;
            }
            for (int y = by; y <= by + 2; y++) { p[y][6] = 1; p[y][25] = 1; }
        }
        return p;
    }

    static int[][] smallTriangle() {
        int[][] p = new int[32][32];
        int cx = 15, top = 8, bot = 22;
        for (int y = top; y <= bot; y++) {
            int hw = (int)((bot - y) * 10.0 / (bot - top));
            p[y][cx - hw] = 1;
            p[y][cx + hw] = 1;
        }
        for (int x = cx - 10; x <= cx + 10; x++) p[bot][x] = 1;
        return p;
    }

    static int[][] wavyLine() {
        int[][] p = new int[32][32];
        for (int y = 0; y < 32; y++) {
            int x = (int)(16 + Math.sin(y * 0.6) * 8);
            if (x >= 0 && x < 32) p[y][x] = 1;
            if (x - 1 >= 0) p[y][x - 1] = 1;
        }
        return p;
    }

    static int[][] smallSquare() {
        int[][] p = new int[32][32];
        int m = 10;
        for (int x = m; x < 32 - m; x++) { p[m][x] = 1; p[31 - m][x] = 1; }
        for (int y = m; y < 32 - m; y++) { p[y][m] = 1; p[y][31 - m] = 1; }
        return p;
    }

    static int[][] smallSpiral() {
        int[][] p = new int[32][32];
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 32; x++) {
                double dx = x - 15.5, dy = y - 15.5;
                double dist = Math.sqrt(dx * dx + dy * dy);
                double angle = Math.atan2(dy, dx);
                double target = dist / 10.0 * Math.PI * 2 + angle;
                if (Math.abs(Math.sin(target * 2)) < 0.3 && dist > 1 && dist < 11) p[y][x] = 1;
            }
        return p;
    }
}
