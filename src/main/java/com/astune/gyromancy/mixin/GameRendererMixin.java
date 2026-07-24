package com.astune.gyromancy.mixin;

import com.astune.gyromancy.client.PaintCameraController;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void gyromancy$cancelPaintCameraViewBob(PoseStack poseStack, float partialTicks, CallbackInfo ci) {
        if (PaintCameraController.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void gyromancy$cancelPaintCameraHurtBob(PoseStack poseStack, float partialTicks, CallbackInfo ci) {
        if (PaintCameraController.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void gyromancy$cancelPaintCameraHand(Camera camera, float partialTick, Matrix4f projectionMatrix, CallbackInfo ci) {
        if (PaintCameraController.isActive()) {
            ci.cancel();
        }
    }
}
