package com.astune.gyromancy.client.glyph;

import com.astune.gyromancy.api.symbol.SymbolTemplate;
import com.astune.gyromancy.registry.GyromancyRegistries;
import com.astune.gyromancy.symbol.ManaPixelDetector;
import com.astune.gyromancy.symbol.SymbolRegistry;
import com.astune.painter.api.CanvasFace;
import com.astune.painter.api.IPixelMatrix;
import com.astune.painter.api.imageProvider.CanvasImageProvider;
import com.astune.painter.api.imageProvider.ImageProviderContext;
import com.astune.painter.event.ServerCanvasUpdateEvent;
import com.mojang.blaze3d.platform.NativeImage;

/**
 * Generates a colored glyph texture from the gyromancy:symbol_id effect layer.
 *
 * <p>Effect-layer value: 0 = no glyph, otherwise symbol registry id + 1.
 */
public final class GlyphImageProvider implements CanvasImageProvider {

    public static final String NAME = "gyromancy_glyph";
    public static final GlyphImageProvider INSTANCE = new GlyphImageProvider();

    private GlyphImageProvider() {}

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean canProvide(ImageProviderContext context) {
        return context.face.getEffectLayer(ManaPixelDetector.SYMBOL_ID_KEY) != null;
    }

    @Override
    public NativeImage createImage(CanvasFace face) {
        byte[] symbolLayer = face.getEffectLayer(ManaPixelDetector.SYMBOL_ID_KEY);
        if (symbolLayer == null) return null;

        IPixelMatrix pixels = face.pixels();
        int w = pixels.getWidth();
        int h = pixels.getHeight();
        if (w * h <= 0) return null;

        NativeImage img = new NativeImage(w, h, true);
        boolean hasAnyGlyph = false;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int symbolValue = symbolLayer[y * w + x] & 0xFF;
                if (symbolValue == 0) continue;

                int abgr = argbToAbgr(glyphColor(symbolValue));
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

    public static int glyphColor(int symbolValue) {
        int registryId = symbolValue - 1;
        SymbolTemplate template = GyromancyRegistries.SYMBOL.byId(registryId);
        return template != null ? template.glyphColor() : SymbolRegistry.DEFAULT_GLYPH_COLOR;
    }

    private static int argbToAbgr(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
