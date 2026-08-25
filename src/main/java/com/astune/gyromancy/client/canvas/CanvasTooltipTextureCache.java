package com.astune.gyromancy.client.canvas;

import com.astune.gyromancy.canvas.CanvasDocument;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reuses GPU textures while the same canvas documents are hovered. */
final class CanvasTooltipTextureCache {
    private static final int MAX_TEXTURES = 32;
    private static final Map<CanvasDocument, CanvasDynamicTexture> TEXTURES =
            new LinkedHashMap<>(16, 0.75F, true);
    private static long nextTextureId;

    private CanvasTooltipTextureCache() {}

    static synchronized CanvasDynamicTexture texture(CanvasDocument document) {
        // Compile caches do not affect the image, so do not create duplicate
        // textures when only recognized-rune metadata changes.
        CanvasDocument visualDocument = document.withCompileCache(List.of(), List.of());
        CanvasDynamicTexture texture = TEXTURES.get(visualDocument);
        if (texture == null) {
            texture = CanvasDynamicTexture.create(
                    "tooltip_" + nextTextureId++, document, false);
            TEXTURES.put(visualDocument, texture);
            trimToLimit();
        }
        return texture;
    }

    static synchronized void clear() {
        TEXTURES.values().forEach(CanvasDynamicTexture::close);
        TEXTURES.clear();
    }

    private static void trimToLimit() {
        Iterator<Map.Entry<CanvasDocument, CanvasDynamicTexture>> iterator =
                TEXTURES.entrySet().iterator();
        while (TEXTURES.size() > MAX_TEXTURES && iterator.hasNext()) {
            CanvasDynamicTexture texture = iterator.next().getValue();
            iterator.remove();
            texture.close();
        }
    }

}
