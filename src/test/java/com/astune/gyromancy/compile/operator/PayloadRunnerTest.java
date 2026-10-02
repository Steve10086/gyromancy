package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.effect.MagicEffect;
import com.astune.gyromancy.entity.ball.MagicBallEntity;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayloadRunnerTest {

    @Test
    void runsEveryServerPayloadInListOrder() {
        boolean[] alive = {true};
        List<String> log = new ArrayList<>();
        MagicEffect effect = effect(alive);

        PayloadRunner.run(List.of(payload(log, "a", false, null),
                payload(log, "b", false, null)), context(effect, false));

        assertEquals(List.of("a", "b"), log);
    }

    @Test
    void skipsPayloadsThatDoNotTickOnTheClient() {
        boolean[] alive = {true};
        List<String> log = new ArrayList<>();
        MagicEffect effect = effect(alive);

        PayloadRunner.run(List.of(payload(log, "server", false, null),
                payload(log, "client", true, null)), context(effect, true));

        assertEquals(List.of("client"), log);
    }

    @Test
    void stopsWhenAPayloadKillsTheEffect() {
        boolean[] alive = {true};
        List<String> log = new ArrayList<>();
        MagicEffect effect = effect(alive);

        PayloadRunner.run(List.of(
                payload(log, "killer", false, () -> alive[0] = false),
                payload(log, "after", false, null)), context(effect, false));

        assertEquals(List.of("killer"), log);
    }

    private static MagicEffect effect(boolean[] alive) {
        return new MagicEffect() {
            @Override
            public Level level() {
                return null;
            }

            @Override
            public Vec3 position() {
                return Vec3.ZERO;
            }

            @Override
            public AABB bounds() {
                return new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
            }

            @Override
            public boolean isAlive() {
                return alive[0];
            }

            @Override
            public void discard() {
                alive[0] = false;
            }

            @Override
            public List<BlockPos> listInside() {
                return List.of();
            }
        };
    }

    private static EntityTickContext context(MagicEffect effect, boolean clientSide) {
        return new EntityTickContext(effect, null, 0, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
                Vec3.ZERO, new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0), clientSide, true,
                true, false, 1.0F, 1.0F, 0.0, new HashMap<>(),
                () -> {}, (Entity entity) -> {}, (MagicBallEntity entity, String key) -> {},
                (DoubleConsumer) value -> {}, (DoubleConsumer) value -> {});
    }

    private static RecordingPayload payload(List<String> log, String name,
                                            boolean clientTicking, Runnable action) {
        return new RecordingPayload(log, name, clientTicking, action);
    }

    private static final class RecordingPayload extends EntityPayload {
        private final List<String> log;
        private final String name;
        private final boolean clientTicking;
        private final Runnable action;

        private RecordingPayload(List<String> log, String name, boolean clientTicking,
                                 Runnable action) {
            this.log = log;
            this.name = name;
            this.clientTicking = clientTicking;
            this.action = action;
        }

        @Override
        public ResourceLocation typeId() {
            return ResourceLocation.fromNamespaceAndPath("gyromancy", "test_" + name);
        }

        @Override
        protected Codec<? extends EntityPayload> codec() {
            return Codec.unit(this);
        }

        @Override
        public void onEntityTick(EntityTickContext ctx) {
            log.add(name);
            if (action != null) action.run();
        }

        @Override
        public boolean ticksOnClient() {
            return clientTicking;
        }
    }
}
