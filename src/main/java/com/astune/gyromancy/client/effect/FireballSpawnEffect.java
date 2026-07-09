package com.astune.gyromancy.client.effect;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.client.render.RenderAnimation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.vertex.VertexArray;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public final class FireballSpawnEffect extends FlipbookEffect {
    private static final int FRAMES = 8;
    private static final float DEFAULT_TICKS_PER_FRAME = 4.0F;
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "textures/effect/magic_mist_vortex_vertical.png");
    private static final ResourceLocation RENDER_TYPE =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "fireball_spawn_flipbook");
    private static final ResourceLocation SHADER =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "fireball_spawn_flipbook");
    private static ShaderRenderer shaderRenderer;
    public static final FireballSpawnEffect INSTANCE = new FireballSpawnEffect();

    private FireballSpawnEffect() {
        super(TEXTURE, FRAMES, DEFAULT_TICKS_PER_FRAME, List.of(
                RenderAnimation.color(0, 0, 0xFFFFFFFF, 0xFFFF2222),
                RenderAnimation.opacity(0, 32, 0, 1),
                RenderAnimation.size(0, 32, new Vec2(2f, 2f), new Vec2(0.2f, 0.2f))
        ));
    }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 center,
                       float ageTicks, float fireballSize, int growthTicks) {
        float ticksPerFrame = ticksPerFrame(growthTicks);
        float scaledAge = ageTicks * DEFAULT_TICKS_PER_FRAME / ticksPerFrame;
        if (shaderRenderer == null) shaderRenderer = new ShaderRenderer();
        shaderRenderer.draw(poseStack, center, scaledAge, fireballSize * 5.0F, fireballSize * 5.0F);
    }

    static float ticksPerFrame(int growthTicks) {
        int animationTicks = (int) (FRAMES * DEFAULT_TICKS_PER_FRAME);
        return growthTicks > 0 && growthTicks < animationTicks
                ? Math.max(0.001F, (float) growthTicks / FRAMES)
                : DEFAULT_TICKS_PER_FRAME;
    }

    private static final class ShaderRenderer {
        private final VertexArray vertexArray = VertexArray.create();

        void draw(PoseStack poseStack, Vec3 center, float ageTicks, float width, float height) {
            ShaderProgram shader = VeilRenderSystem.renderer().getShaderManager().getShader(SHADER);
            if (shader == null || !shader.isValid()) return;

            RenderAnimation.State animation = RenderAnimation.evaluate(ageTicks, INSTANCE.actions);
            int frame = FlipbookEffect.frame((int) ageTicks, FRAMES, DEFAULT_TICKS_PER_FRAME);
            float v0 = (float) frame / FRAMES;
            float v1 = (float) (frame + 1) / FRAMES;
            float halfWidth = width * animation.size.x * 0.5F;
            float halfHeight = height * animation.size.y * 0.5F;

            PoseStack worldPose = new PoseStack();
            worldPose.translate(center.x, center.y, center.z);
            RenderAnimation.applyPose(worldPose, animation, false);
            PoseStack.Pose pose = worldPose.last();

            BufferBuilder builder = RenderSystem.renderThreadTesselator()
                    .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            addVertex(builder, pose, -halfWidth, 0.0F, -halfHeight, 0.0F, v1, animation);
            addVertex(builder, pose, halfWidth, 0.0F, -halfHeight, 1.0F, v1, animation);
            addVertex(builder, pose, halfWidth, 0.0F, halfHeight, 1.0F, v0, animation);
            addVertex(builder, pose, -halfWidth, 0.0F, halfHeight, 0.0F, v0, animation);

            MeshData mesh = builder.buildOrThrow();
            RenderType renderType = renderType();
            if (renderType == null) return;
            vertexArray.upload(mesh, VertexArray.DrawUsage.STREAM);
            vertexArray.bind();
            vertexArray.setup(renderType);
            RenderSystem.setShaderTexture(0, TEXTURE);
            shader.bind();
            shader.setDefaultUniforms(VertexFormat.Mode.QUADS);
            shader.getUniform("GlowStrength").setFloat(1.35F);
            shader.bindSamplers(0);
            RenderSystem.enableBlend();
            RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
            RenderSystem.blendFunc(GL11.GL_ONE, GL11.GL_ONE);
            RenderSystem.depthMask(false);
            try {
                vertexArray.draw();
            } finally {
                shader.clearSamplers();
                ShaderProgram.unbind();
                vertexArray.clear(renderType);
                RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableBlend();
                RenderSystem.depthMask(true);
                VertexArray.unbind();
            }
        }

        private static void addVertex(BufferBuilder builder, PoseStack.Pose pose,
                                      float x, float y, float z, float u, float v,
                                      RenderAnimation.State animation) {
            builder.addVertex(pose, x, y, z)
                    .setUv(u, v)
                    .setColor(animation.argb());
        }

        private static RenderType renderType() {
            return VeilRenderType.get(RENDER_TYPE);
        }
    }
}
