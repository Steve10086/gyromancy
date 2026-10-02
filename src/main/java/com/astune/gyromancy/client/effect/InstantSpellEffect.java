package com.astune.gyromancy.client.effect;

import com.lowdragmc.photon.client.fx.*;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * A position-anchored Photon FX with no owning entity. It plays at a fixed
 * world position and stops itself once the runtime ends or a safety lifetime
 * expires.
 */
@OnlyIn(Dist.CLIENT)
public final class InstantSpellEffect {
    private static final ResourceLocation FX_ID =
            ResourceLocation.fromNamespaceAndPath("gyromancy", "crush");
    private static final int MAX_AGE_TICKS = 100;

    private final Level level;
    private final Vec3 position;
    private final DynamicEffectProperties properties = new DynamicEffectProperties();
    private @Nullable FX fx;
    private @Nullable Runner runner;
    private boolean started;
    private int age;

    InstantSpellEffect(Level level, Vec3 position, float size, int color) {
        this.level = level;
        this.position = position;
        properties.setSize(size);
        properties.setColor(color);
    }

    void start() {
        if (started) return;
        fx = FXHelper.getFX(FX_ID);
        if (fx == null) return;
        started = true;
        runner = new Runner(fx, level, position, properties);
        runner.emit();
    }

    void tick() {
        if (!started) return;
        age++;
        if (age > MAX_AGE_TICKS || runner == null || !runner.isAlive()) stop();
    }

    boolean isAlive() {
        return started && runner != null && runner.isAlive();
    }

    void stop() {
        if (runner != null) {
            runner.kill();
            runner = null;
        }
        started = false;
    }

    private static final class Runner extends FXEffectExecutor {
        private final Vec3 position;
        private final DynamicEffectProperties properties;

        Runner(FX fx, Level level, Vec3 position, DynamicEffectProperties properties) {
            super(fx, level);
            this.position = position;
            this.properties = properties;
            this.allowMulti = true;
            this.forcedDeath = true;
        }

        @Override
        public void start() {
            // handled by emit()
        }

        @Override
        public void updateFXObjectFrame(IFXObject obj, float partialTicks) {
            if (getRuntime() == null || obj != getRuntime().getRoot()) return;
            properties.apply(getRuntime());
            Vec3 pos = properties.offset(position);
            getRuntime().getRoot().updatePos(new Vector3f((float) pos.x, (float) pos.y, (float) pos.z));
        }

        @Override
        public void updateFXObjectTick(IFXObject obj) {
            // Fixed position; nothing to keep in sync.
        }

        void emit() {
            if (this.runtime != null) return;
            this.runtime = fx.createRuntime(true);
            Vec3 pos = properties.offset(position);
            this.runtime.getRoot().updatePos(new Vector3f((float) pos.x, (float) pos.y, (float) pos.z));
            properties.apply(this.runtime);
            this.runtime.emmit(this, 0);
        }

        boolean isAlive() {
            return runtime != null && runtime.isValid() && runtime.isAlive();
        }

        void kill() {
            if (this.runtime != null) {
                this.runtime.destroy(true);
                this.runtime = null;
            }
        }
    }
}
