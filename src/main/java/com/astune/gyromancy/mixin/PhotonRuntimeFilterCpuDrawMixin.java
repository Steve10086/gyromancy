package com.astune.gyromancy.mixin;

import com.astune.gyromancy.client.effect.PhotonRuntimeFilterLayer;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.IMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.MaterialContext;
import com.mojang.blaze3d.vertex.VertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rebinds FxColor after a CPU material&apos;s scene-sampler setup may have rebound DRAW_TARGET. */
@Mixin(targets = "com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.PhotonFXRenderPass", remap = false)
public abstract class PhotonRuntimeFilterCpuDrawMixin {

    @Inject(method = "renderWithMaterial", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V", shift = At.Shift.BEFORE), remap = false)
    private void gyromancy$bindFxColorBeforeCpuDraw(IMaterial material, MaterialContext context,
                                                    VertexBuffer vbo, CallbackInfo ci) {
        PhotonRuntimeFilterLayer.bindCaptureTargetForDraw();
    }
}
