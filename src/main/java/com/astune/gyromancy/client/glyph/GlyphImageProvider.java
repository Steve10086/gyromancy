package com.astune.gyromancy.client.glyph;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.symbol.GlyphMarker;
import com.astune.gyromancy.symbol.ManaPixelDetector;
import com.astune.painter.api.CanvasFace;
import com.astune.painter.api.IPixelMatrix;
import com.astune.painter.api.imageProvider.CanvasImageProvider;
import com.astune.painter.api.imageProvider.ImageProviderContext;
import com.mojang.blaze3d.platform.NativeImage;

/**
 * Generates a colored glyph texture from the {@code gyromancy:glyph_id} effect layer.
 *
 * <p>Effect-layer byte encoding (set by {@link GlyphMarker#markConsumed}):
 * <pre>
 *   encoded = (colorIndex << 6) | (sequence & 0x3F)
 * </pre>
 *
 * <p>Color palette:
 * <ul>
 *   <li>0 → transparent (no glyph)</li>
 *   <li>1 → Red   {@code 0xFFFF0000} (Fire)</li>
 *   <li>2 → Blue  {@code 0xFF0000FF} (Water)</li>
 *   <li>3 → Brown {@code 0xFF8B4513} (Earth)</li>
 * </ul>
 *
 * <p>Registered in {@code ClientSetup} and resolved by Pigmentum's
 * {@code CanvasTextureManager} for each canvas face that has glyph data.
 */
public final class GlyphImageProvider implements CanvasImageProvider {

    public static final String NAME = "gyromancy_glyph";
    public static final GlyphImageProvider INSTANCE = new GlyphImageProvider();

    /** Color palette: index → ARGB */
    private static final int[] COLORS = {
            0x00000000,  // 0: transparent
            0xFFFF0000,  // 1: Fire   → Red
            0xFF0000FF,  // 2: Water  → Blue
            0xFF8B4513,  // 3: Earth  → Brown (saddle brown)
    };

    private GlyphImageProvider() {}

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean canProvide(ImageProviderContext context) {
        return context.face.getEffectLayer(ManaPixelDetector.GLYPH_ID_KEY) != null;
    }

    @Override
    public NativeImage createImage(CanvasFace face) {
        byte[] glyphLayer = face.getEffectLayer(ManaPixelDetector.GLYPH_ID_KEY);
        if (glyphLayer == null) return null;

        IPixelMatrix pixels = face.pixels();
        int w = pixels.getWidth();
        int h = pixels.getHeight();

        if (w * h <= 0) return null;

        NativeImage img = new NativeImage(w, h, true);
        boolean hasAnyGlyph = false;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int encoded = glyphLayer[y * w + x] & 0xFF;
                if (encoded == 0) continue;

                int colorIndex = GlyphMarker.decodeColorIndex(encoded);
                if (colorIndex <= 0 || colorIndex >= COLORS.length) continue;

                int argb = COLORS[colorIndex];
                // ARGB → ABGR (NativeImage convention)
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                int abgr = (a << 24) | (b << 16) | (g << 8) | r;

                img.setPixelRGBA(x, y, abgr);
                hasAnyGlyph = true;
            }
        }

        if (!hasAnyGlyph) {
            img.close();
            return null;
        }

        return img;
    }
}
