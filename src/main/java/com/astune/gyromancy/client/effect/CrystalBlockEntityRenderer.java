package com.astune.gyromancy.client.effect;

import com.astune.gyromancy.block.CrystalBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws the crystal model with its short spawn grow-in animation.
 *
 * <p>The block state stays on {@code ENTITYBLOCK_ANIMATED} so the chunk
 * renderer never draws the static model; the baked model is drawn here
 * directly (the same way {@code renderSingleBlock} does for {@code MODEL}
 * blocks) so it can be scaled while the crystal grows in.
 */
@OnlyIn(Dist.CLIENT)
public final class CrystalBlockEntityRenderer implements BlockEntityRenderer<CrystalBlockEntity> {
    /** Matches the model lookup seed used by {@code renderSingleBlock}. */
    private static final long RENDER_SEED = 42L;

    private final BlockRenderDispatcher blockRenderer;
    private final ModelBlockRenderer modelRenderer;

    public CrystalBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
        this.modelRenderer = blockRenderer.getModelRenderer();
    }

    @Override
    public void render(CrystalBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = blockEntity.getLevel();
        BlockState state = blockEntity.getBlockState();
        float scale = level == null ? 1.0F : CrystalSpawnEffects.scale(
                blockEntity.getBlockPos(), level.getGameTime(), partialTick);
        poseStack.pushPose();
        if (scale < 1.0F) {
            // Grow upwards from the bottom of the block so the crystal looks planted.
            poseStack.translate(0.5F, 0.0F, 0.5F);
            poseStack.scale(scale, scale, scale);
            poseStack.translate(-0.5F, 0.0F, -0.5F);
        }
        BakedModel model = blockRenderer.getBlockModel(state);
        RandomSource random = RandomSource.create(RENDER_SEED);
        for (RenderType renderType : model.getRenderTypes(state, random, ModelData.EMPTY)) {
            modelRenderer.renderModel(
                    poseStack.last(),
                    bufferSource.getBuffer(RenderTypeHelper.getEntityRenderType(renderType, false)),
                    state,
                    model,
                    1.0F, 1.0F, 1.0F,
                    packedLight,
                    packedOverlay,
                    ModelData.EMPTY,
                    renderType);
        }
        poseStack.popPose();
    }
}
