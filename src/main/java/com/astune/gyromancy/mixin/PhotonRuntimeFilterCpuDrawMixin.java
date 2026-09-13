package com.astune.gyromancy.mixin;

import com.astune.gyromancy.client.effect.PhotonRuntimeFilterLayer;
import com.mojang.blaze3d.vertex.VertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restores FxColor after ShaderInstance.apply() resolved dynamic Photon scene samplers. */
@Mixin(VertexBuffer.class)
public abstract class PhotonRuntimeFilterCpuDrawMixin {

    @Inject(
            method = "_drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ShaderInstance;apply()V", shift = At.Shift.AFTER)
    )
    private void gyromancy$bindFxColorAfterShaderApply(CallbackInfo ci) {
        PhotonRuntimeFilterLayer.bindCaptureTargetForDraw();
    }
}
