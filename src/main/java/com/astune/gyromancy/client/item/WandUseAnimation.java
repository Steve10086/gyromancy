package com.astune.gyromancy.client.item;

import net.minecraft.util.Mth;

/** Small deterministic first-person motion curves for the held wand action. */
final class WandUseAnimation {
    static final float EXTENSION_DISTANCE = 0.1F;
    static final float EXTENSION_TICKS = 7.0F;
    static final float TREMBLE_AMPLITUDE = 0.005F;
    static final float TREMBLE_ANGULAR_SPEED = 0.1F;

    private WandUseAnimation() {}

    static float forwardExtension(float usingTicks) {
        return EXTENSION_DISTANCE * extensionProgress(usingTicks);
    }

    static float extensionProgress(float usingTicks) {
        float progress = Mth.clamp(usingTicks / EXTENSION_TICKS, 0.0F, 1.0F);
        return progress * progress * (3.0F - 2.0F * progress);
    }

    static float lift(float extensionProgress) {
        return 0.06F * Mth.clamp(extensionProgress, 0.0F, 1.0F);
    }

    static float pitchDegrees(float extensionProgress) {
        return 8.0F * Mth.clamp(extensionProgress, 0.0F, 1.0F);
    }

    static float verticalOffset(float usingTicks) {
        return Mth.sin(usingTicks * TREMBLE_ANGULAR_SPEED) * TREMBLE_AMPLITUDE;
    }
}
