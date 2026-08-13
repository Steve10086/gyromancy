package com.astune.gyromancy.client.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WandUseAnimationTest {
    @Test
    void forwardExtensionEasesInAndReachesHoldDistance() {
        assertEquals(0.0F, WandUseAnimation.forwardExtension(0.0F), 1.0E-6F);
        assertTrue(WandUseAnimation.forwardExtension(3.5F)
                > WandUseAnimation.forwardExtension(1.0F));
        assertEquals(WandUseAnimation.EXTENSION_DISTANCE,
                WandUseAnimation.forwardExtension(20.0F), 1.0E-6F);
    }

    @Test
    void verticalMotionOscillatesAroundTheHeldPosition() {
        float first = WandUseAnimation.verticalOffset(1.0F);
        float opposite = WandUseAnimation.verticalOffset(
                1.0F + (float) (Math.PI / WandUseAnimation.TREMBLE_ANGULAR_SPEED));

        assertEquals(-first, opposite, 1.0E-5F);
        assertTrue(Math.abs(first) <= WandUseAnimation.TREMBLE_AMPLITUDE);
    }

    @Test
    void extensionRaisesAndTiltsTheWandWithTheSameEasedProgress() {
        assertEquals(0.0F, WandUseAnimation.lift(0.0F), 1.0E-6F);
        assertEquals(0.0F, WandUseAnimation.pitchDegrees(0.0F), 1.0E-6F);
        assertEquals(0.06F, WandUseAnimation.lift(1.0F), 1.0E-6F);
        assertEquals(8.0F, WandUseAnimation.pitchDegrees(1.0F), 1.0E-6F);
    }
}
