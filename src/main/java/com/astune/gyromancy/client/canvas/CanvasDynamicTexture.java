package com.astune.gyromancy.client.canvas;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.canvas.CanvasDocument;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

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
    private boolean dirty;
    private boolean closed;

    private CanvasDynamicTexture(String name,
                                 int width,
                                 int height,
                                 int[] colors,
                                 boolean mirrorX) {
        this.textureManager = Minecraft.getInstance().getTextureManager();
        this.width = width;
        this.height = height;
        this.mirrorX = mirrorX;

        NativeImage image = new NativeImage(width, height, true);
        writeAll(image, colors);
        this.texture = new DynamicTexture(image);
        this.texture.setFilter(false, false);
        this.location = ResourceLocation.fromNamespaceAndPath(
                Gyromancy.MODID,
                "dynamic/canvas/" + name);
        textureManager.register(location, texture);
    }

    static CanvasDynamicTexture create(String name,
                                       CanvasDocument document,
                                       boolean mirrorX) {
        return new CanvasDynamicTexture(
                name,
                document.resolutionWidth(),
                document.resolutionHeight(),
                document.colors(),
                mirrorX);
    }

    static CanvasDynamicTexture create(String name,
                                       int scale,
                                       int[] colors,
                                       boolean mirrorX) {
        int size = CanvasDocument.PIXELS_PER_BLOCK * scale;
        return new CanvasDynamicTexture(name, size, size, colors, mirrorX);
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
        image.setPixelRGBA(
                textureX,
                y,
                CanvasTexturePixels.argbToAbgr(
                        CanvasTexturePixels.compositeOverPaper(strokeArgb)));
        dirty = true;
    }

    void replacePixels(int[] colors) {
        writeAll(pixels(), colors);
        dirty = true;
    }

    void uploadIfDirty() {
        if (!closed && dirty) {
            texture.upload();
            dirty = false;
        }
    }

    private void writeAll(NativeImage image, int[] colors) {
        int[] composed = CanvasTexturePixels.compose(width, height, colors, mirrorX);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setPixelRGBA(
                        x,
                        y,
                        CanvasTexturePixels.argbToAbgr(composed[y * width + x]));
            }
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
