package com.astune.gyromancy.mixin;

import com.astune.gyromancy.client.effect.PhotonRuntimeFilterLayer;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/** Diverts only explicitly FxColor-aware post effects from Photon&apos;s frame-end stack. */
@Mixin(targets = "com.lowdragmc.photon.client.postfx.runtime.PostEffectStack", remap = false)
public abstract class PhotonRuntimeFilterPostEffectMixin {

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true, remap = false)
    private void gyromancy$routeRuntimeFilter(IResourcePath effect, Map<String, Object> params,
                                               float weight, CallbackInfo ci) {
        if (PhotonRuntimeFilterLayer.registerPostEffect(effect, params, weight)) {
            ci.cancel();
        }
    }
}
