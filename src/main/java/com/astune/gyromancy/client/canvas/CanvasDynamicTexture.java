package com.astune.gyromancy.client.canvas;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * One registered GPU texture containing both the canvas paper and its strokes.
 *
 * <p>Pixel writes are batched until the next render. Replacing or closing this
 * object releases both the texture registration and its native image.
 */
final class CanvasDynamicTexture implements AutoCloseable {
    private final TextureManager textureManager;
    private final DynamicTexture texture;
    private final ResourceLocation location;
    private final int width;
    private final int height;
    private final boolean mirrorX;
    private final int[] background;
    private boolean dirty;
    private boolean closed;

    private CanvasDynamicTexture(String name,
                                 int width,
                                 int height,
                                 int[] colors,
                                 boolean mirrorX,
                                 int[] background) {
        this.textureManager = Minecraft.getInstance().getTextureManager();
        this.width = width;
        this.height = height;
        this.mirrorX = mirrorX;
        this.background = background;

        NativeImage image = new NativeImage(width, height, true);
        writeAll(image, colors);
        this.texture = new DynamicTexture(image);
        this.location = ResourceLocation.fromNamespaceAndPath(
                Gyromancy.MODID,
                "dynamic/canvas/" + name);
        textureManager.register(location, texture);
        // Registration may reapply the texture's default sampler state.
        // Set nearest-neighbour filtering only after it is registered.
        enforceNearestFilter();
    }

    static CanvasDynamicTexture create(String name,
                                       CanvasDocument document,
                                       boolean mirrorX) {
        return new CanvasDynamicTexture(
                name,
                document.resolutionWidth(),
                document.resolutionHeight(),
                document.colors(),
                mirrorX,
                null);
    }

    static CanvasDynamicTexture create(String name,
                                       int scale,
                                       int[] colors,
                                       boolean mirrorX) {
        int size = CanvasDocument.PIXELS_PER_BLOCK * scale;
        return new CanvasDynamicTexture(name, size, size, colors, mirrorX, null);
    }

    static CanvasDynamicTexture createWithMaterial(String name,
                                                   int scale,
                                                   int[] colors,
                                                   boolean mirrorX,
                                                   ResourceLocation material) {
        int size = CanvasDocument.PIXELS_PER_BLOCK * scale;
        return new CanvasDynamicTexture(
                name,
                size,
                size,
                colors,
                mirrorX,
                loadMaterialPixels(material, size, size));
    }

    ResourceLocation location() {
        return location;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    void setCanvasPixel(int matrixX, int y, int strokeArgb) {
        NativeImage image = pixels();
        int textureX = CanvasTexturePixels.textureX(matrixX, width, mirrorX);
        int displayedArgb = background == null
                ? CanvasTexturePixels.compositeOverPaper(strokeArgb)
                : CanvasTexturePixels.compositeOver(
                        strokeArgb, background[y * width + matrixX]);
        image.setPixelRGBA(
                textureX, y, CanvasTexturePixels.argbToAbgr(displayedArgb));
        dirty = true;
    }

    void replacePixels(int[] colors) {
        writeAll(pixels(), colors);
        dirty = true;
    }

    void uploadIfDirty() {
        if (!closed && dirty) {
            texture.upload();
            enforceNearestFilter();
            dirty = false;
        }
    }

    void enforceNearestFilter() {
        if (!closed) texture.setFilter(false, false);
    }

    private void writeAll(NativeImage image, int[] colors) {
        int[] composed = background == null
                ? CanvasTexturePixels.compose(width, height, colors, mirrorX)
                : CanvasTexturePixels.composeOverBackground(
                        width, height, colors, background, mirrorX);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setPixelRGBA(
                        x,
                        y,
                        CanvasTexturePixels.argbToAbgr(composed[y * width + x]));
            }
        }
    }

    private static int[] loadMaterialPixels(ResourceLocation material,
                                            int targetWidth,
                                            int targetHeight) {
        int[] fallback = new int[targetWidth * targetHeight];
        Arrays.fill(fallback, CanvasTexturePixels.PAPER_ARGB);
        try {
            Resource resource = Minecraft.getInstance()
                    .getResourceManager()
                    .getResource(material)
                    .orElse(null);
            if (resource == null) {
                Gyromancy.LOGGER.warn(
                        "Stamp canvas material texture {} was not found",
                        material);
                return fallback;
            }
            try (InputStream stream = resource.open();
                 NativeImage source = NativeImage.read(stream)) {
                int sourceWidth = source.getWidth();
                int sourceHeight = source.getHeight();
                int[] sourcePixels = new int[sourceWidth * sourceHeight];
                for (int y = 0; y < sourceHeight; y++) {
                    for (int x = 0; x < sourceWidth; x++) {
                        sourcePixels[y * sourceWidth + x] =
                                CanvasTexturePixels.argbToAbgr(
                                        source.getPixelRGBA(x, y));
                    }
                }
                return CanvasTexturePixels.resizeNearest(
                        sourcePixels,
                        sourceWidth,
                        sourceHeight,
                        targetWidth,
                        targetHeight);
            }
        } catch (IOException exception) {
            Gyromancy.LOGGER.warn(
                    "Could not load stamp canvas material texture {}",
                    material,
                    exception);
            return fallback;
        }
    }

    private NativeImage pixels() {
        if (closed || texture.getPixels() == null) {
            throw new IllegalStateException("Canvas texture is already closed");
        }
        return texture.getPixels();
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            textureManager.release(location);
        }
    }
}
