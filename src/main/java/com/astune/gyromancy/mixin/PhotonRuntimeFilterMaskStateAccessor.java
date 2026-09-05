package com.astune.gyromancy.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Enables PhotonFXRenderPass&apos;s own CustomMask/CustomDepth material branch for one private replay. */
@Mixin(targets = "com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline", remap = false)
public interface PhotonRuntimeFilterMaskStateAccessor {

    @Accessor("maskSubPass")
    void gyromancy$setMaskSubPass(boolean maskSubPass);
}
