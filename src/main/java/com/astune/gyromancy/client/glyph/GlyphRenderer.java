package com.astune.gyromancy.client.glyph;

import com.astune.painter.api.CanvasFace;
import com.astune.painter.api.render.CanvasPixelRenderer;
import com.astune.painter.api.render.RenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Renders the glyph overlay texture on canvas faces at full brightness.
 *
 * <p>Registered once for all glyph colors — the texture already contains
 * the correct per-pixel color from {@link GlyphImageProvider}.
 * The renderer only needs to identify the glyph texture by its
 * provider name in the resource path and draw a lit quad.
 *
 * <p>Uses <em>full brightness</em> ({@code 0x00F000F0}) so glyphs are
 * always clearly visible regardless of ambient lighting.
 */
public final class GlyphRenderer implements CanvasPixelRenderer {

    public static final GlyphRenderer INSTANCE = new GlyphRenderer();

    /** Full-brightness packed light: block-light=15, sky-light=15. */
    private static final int FULL_BRIGHT = 0x00F000F0;

    private GlyphRenderer() {}

    /**
     * Identifies the glyph texture by the provider name embedded in the path.
     * Pigmentum's {@code CanvasTextureManager} names textures as
     * {@code {entityId}_{faceIndex}_{providerName}_{counter}}.
     */
    @Override
    public boolean canRender(RenderContext context) {
        return context.texture != null
                && context.texture.getPath().contains(GlyphImageProvider.NAME);
    }

    @Override
    public boolean renderFace(RenderContext context) {
        if (context.texture == null) return false;

        CanvasFace face = context.face;
        Vec3[] corners = face.cornerWithOffset(context.offset);
        Direction dir = face.primaryFace();
        Vec3 normal = Vec3.atLowerCornerOf(dir.getNormal());
        float nx = (float) normal.x;
        float ny = (float) normal.y;
        float nz = (float) normal.z;

        VertexConsumer vc = context.bufferSource.getBuffer(
                RenderType.entityTranslucent(context.texture));
        PoseStack.Pose pose = context.poseStack.last();

        // Quad with full brightness, no ambient occlusion
        addVertex(vc, pose, corners[0], 0, 0, nx, ny, nz, FULL_BRIGHT, context.packedOverlay);
        addVertex(vc, pose, corners[1], 1, 0, nx, ny, nz, FULL_BRIGHT, context.packedOverlay);
        addVertex(vc, pose, corners[2], 1, 1, nx, ny, nz, FULL_BRIGHT, context.packedOverlay);
        addVertex(vc, pose, corners[3], 0, 1, nx, ny, nz, FULL_BRIGHT, context.packedOverlay);

        return true; // face rendered — stop trying further renderers
    }

    private static void addVertex(VertexConsumer vc, PoseStack.Pose pose, Vec3 pos,
                                   float u, float v, float nx, float ny, float nz,
                                   int light, int overlay) {
        vc.addVertex(pose, (float) pos.x, (float) pos.y, (float) pos.z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
