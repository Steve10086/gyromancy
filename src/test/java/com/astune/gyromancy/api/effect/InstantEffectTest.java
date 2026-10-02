package com.astune.gyromancy.api.effect;

import com.astune.gyromancy.compile.operator.EntityPayload;
import com.astune.gyromancy.compile.operator.EntityTickContext;
import com.astune.gyromancy.compile.operator.OnEntityTickOp;
import com.astune.gyromancy.compile.operator.TriggerOp;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class InstantEffectTest {

    @Test
    void executesEntityTickPayloadsOnceThenFinishes() {
        RecordingPayload payload = new RecordingPayload();
        InstantEffect effect = effect(List.of(payload));

        effect.executeOnce();

        assertEquals(1, payload.ticks);
        assertEquals(1, payload.removals);
        assertFalse(effect.isAlive());

        effect.executeOnce();
        assertEquals(1, payload.ticks);
        assertEquals(1, payload.removals);
    }

    @Test
    void triggerConditionsEvaluateExactlyOnce() {
        int[] checks = {0};
        InstantEffect effect = effect(List.of(new CountingTrigger(checks)));

        effect.executeOnce();

        assertEquals(1, checks[0]);
    }

    @Test
    void discardRunsRemovalHooksOnlyOnce() {
        RecordingPayload payload = new RecordingPayload();
        InstantEffect effect = effect(List.of(payload));

        effect.discard();
        effect.discard();

        assertEquals(1, payload.removals);
    }

    @Test
    void listsEveryVoxelTouchingTheFixedSphere() {
        InstantEffect effect = new InstantEffect(null, new Vec3(0.5, 64.5, 0.5), 1.0,
                new Vec3(0.0, 1.0, 0.0), List.of());

        // Radius one from a voxel centre reaches the eight neighbouring voxel
        // corners, so all 27 voxels of the 3x3x3 neighbourhood are touched.
        assertEquals(27, effect.listInside().size());
    }

    private static InstantEffect effect(List<EntityPayload> payload) {
        return new InstantEffect(null, new Vec3(0.5, 64.5, 0.5), 2.0,
                new Vec3(0.0, 1.0, 0.0), payload);
    }

    private static final class RecordingPayload extends OnEntityTickOp {
        private int ticks;
        private int removals;

        @Override
        public ResourceLocation typeId() {
            return ResourceLocation.fromNamespaceAndPath("gyromancy", "test_recording");
        }

        @Override
        protected Codec<? extends EntityPayload> codec() {
            return Codec.unit(this);
        }

        @Override
        public void onEntityTick(EntityTickContext ctx) {
            ticks++;
        }

        @Override
        public void onOwnerRemoved(Level level, MagicEffect owner) {
            removals++;
        }
    }

    private static final class CountingTrigger extends TriggerOp {
        private final int[] checks;

        private CountingTrigger(int[] checks) {
            this.checks = checks;
        }

        @Override
        public ResourceLocation typeId() {
            return ResourceLocation.fromNamespaceAndPath("gyromancy", "test_trigger");
        }

        @Override
        protected Codec<? extends EntityPayload> codec() {
            return Codec.unit(this);
        }

        @Override
        protected boolean shouldTrigger(EntityTickContext ctx) {
            checks[0]++;
            return false;
        }

        @Override
        protected void trigger(EntityTickContext ctx) {
        }
    }
}
