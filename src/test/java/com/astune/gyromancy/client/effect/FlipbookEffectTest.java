package com.astune.gyromancy.client.effect;

import com.astune.gyromancy.client.render.RenderAnimation;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlipbookEffectTest {
    @Test
    void frameLoopsByTicksPerFrame() {
        assertEquals(0, FlipbookEffect.frame(0, 4, 2.0F));
        assertEquals(0, FlipbookEffect.frame(1, 4, 2.0F));
        assertEquals(1, FlipbookEffect.frame(2, 4, 2.0F));
        assertEquals(3, FlipbookEffect.frame(7, 4, 2.0F));
        assertEquals(0, FlipbookEffect.frame(8, 4, 2.0F));
    }

    @Test
    void compositionRunsChildActionsInLocalTime() {
        RenderAnimation.State state = RenderAnimation.evaluate(15, List.of(
                RenderAnimation.composition(10, 20, List.of(
                        RenderAnimation.move(0, 20, new Vec3(0, 2, 0)),
                        RenderAnimation.opacity(0, 20, 0, 1),
                        RenderAnimation.color(0, 20, 0xFFFF0000, 0xFF0000FF)
                ))
        ));

        assertEquals(0.5, state.offset.y, 1e-6);
        assertEquals(0.25f, state.alpha, 1e-6f);
        assertEquals(0x40BF0040, state.argb());
    }

    @Test
    void fireballSpawnEffectSpeedsUpOnlyForShortGrowth() {
        assertEquals(4.0f, FireballSpawnEffect.ticksPerFrame(50), 1e-6f);
        assertEquals(2.0f, FireballSpawnEffect.ticksPerFrame(16), 1e-6f);
    }

    @Test
    void sizeActionInterpolatesStateSize() {
        RenderAnimation.State state = RenderAnimation.evaluate(8, List.of(
                RenderAnimation.size(0, 16, new Vec2(1.2F, 1.2F), new Vec2(0.2F, 0.2F))
        ));

        assertEquals(0.7F, state.size.x, 1e-6F);
        assertEquals(0.7F, state.size.y, 1e-6F);
    }
}
