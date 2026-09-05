package com.astune.gyromancy.mixin;

import com.astune.gyromancy.client.effect.PhotonRuntimeFilterLayer;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Covers Photon&apos;s instanced tile, beam and trail paths, which bypass renderWithMaterial. */
@Mixin(targets = "com.lowdragmc.photon.client.gameobject.particle.renderer.InstancedRenderBackend", remap = false)
public abstract class PhotonRuntimeFilterInstancedDrawMixin {

    @Inject(method = "drawWithShader", at = @At("HEAD"), remap = false)
    private void gyromancy$bindFxColorBeforeInstancedDraw(ShaderInstance shader, CallbackInfo ci) {
        PhotonRuntimeFilterLayer.bindCaptureTargetForDraw();
    }
}
