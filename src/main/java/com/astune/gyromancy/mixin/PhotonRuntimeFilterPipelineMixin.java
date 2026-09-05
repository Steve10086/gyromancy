package com.astune.gyromancy.mixin;

import com.astune.gyromancy.client.effect.PhotonRuntimeFilterLayer;
import com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.PhotonFXRenderPass;
import com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline;
import com.lowdragmc.photon.client.gameobject.particle.IParticle;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/** A focused call-site bridge: Photon keeps the queue loop and we only wrap one pass draw. */
@Mixin(targets = "com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline", remap = false)
public abstract class PhotonRuntimeFilterPipelineMixin {

    @Redirect(
            method = "renderQueuedPasses",
            at = @At(value = "INVOKE", target = "Lcom/lowdragmc/photon/client/gameobject/emitter/renderpipeline/PhotonFXRenderPass;drawParticles(Lcom/lowdragmc/photon/client/gameobject/emitter/renderpipeline/RenderPassPipeline;Ljava/util/Collection;Lnet/minecraft/client/Camera;F)Z"),
            remap = false)
    private boolean gyromancy$drawThroughRuntimeFilter(PhotonFXRenderPass pass,
                                                        RenderPassPipeline pipeline,
                                                        Collection<IParticle> particles,
                                                        Camera camera, float partialTicks) {
        return PhotonRuntimeFilterLayer.drawParticles(pass, pipeline, particles, camera, partialTicks);
    }

    @Inject(method = "renderQueuedPasses", at = @At("RETURN"), remap = false)
    private void gyromancy$flushRuntimeFilter(CallbackInfo ci) {
        PhotonRuntimeFilterLayer.flushOpenSegment((RenderPassPipeline) (Object) this);
    }

    @Inject(method = "isPremultipliedAccumulation", at = @At("HEAD"), cancellable = true, remap = false)
    private void gyromancy$useLayerSafeBlendForCapture(CallbackInfoReturnable<Boolean> cir) {
        if (PhotonRuntimeFilterLayer.usesPremultipliedCaptureBlend()) {
            cir.setReturnValue(true);
        }
    }
}
