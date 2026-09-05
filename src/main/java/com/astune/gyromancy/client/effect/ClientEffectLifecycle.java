package com.astune.gyromancy.client.effect;

import com.astune.gyromancy.client.array.ArrayClientState;
import com.astune.gyromancy.client.canvas.CanvasClientState;
import com.astune.gyromancy.client.compat.iris.IrisRenderBridge;
import com.astune.gyromancy.client.entity.ElementBallRenderer;
import com.astune.gyromancy.client.entity.FireballRenderer;
import com.astune.gyromancy.client.entity.ManaballRenderer;
import com.astune.gyromancy.client.entity.OldFireballRenderer;
import com.lowdragmc.photon.client.compat.iris.IrisCompat;
import com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.RenderPassPipeline;
import com.lowdragmc.photon.client.postfx.runtime.PostFXTargetPool;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Coordinates client-only effect and render-target teardown on level changes. */
public final class ClientEffectLifecycle {
    private ClientEffectLifecycle() {}

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;

        // The unload event is fired before Photon receives ParticleEngine.setLevel
        // and before Iris creates the next shader pipeline. Drop every reference
        // that belongs to the old level before either library can render again.
        ArrayClientState.onLevelUnload(event);
        CanvasClientState.onLevelUnload(event);
        FlipbookEffect.clearAll();
        EntityEffect.clearAll();
        VortexOrbitEffect.clearAll();
        ElementBallRenderer.clearClientState();
        FireballRenderer.clearClientState();
        ManaballRenderer.clearClientState();
        OldFireballRenderer.clearClientState();
        PhotonRuntimeFilterLayer.onClientLevelUnload();
        invalidatePhotonRenderState();
        WandProjectionGlowRenderer.onClientLevelUnload();
        IrisRenderBridge.onClientLevelUnload();
    }

    private static void invalidatePhotonRenderState() {
        // Photon keeps these targets static and normally refreshes them only
        // from its window-resize hook. A dimension change replaces Iris'
        // depth/color attachments without changing the window dimensions.
        RenderPassPipeline.markDrawTargetDirty();
        RenderPassPipeline.clearFrameMask();
        PostFXTargetPool.invalidateAll();
        IrisCompat.invalidate();
    }
}
