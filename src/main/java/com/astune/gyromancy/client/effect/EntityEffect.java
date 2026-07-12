package com.astune.gyromancy.client.effect;

import com.lowdragmc.photon.client.fx.*;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * Attaches a Photon FX to an entity with dynamic size, opacity, and position offset.
 *
 * <pre>
 *     var fx = new EntityEffect(entity, ResourceLocation.parse("gyromancy:srounding_fire"))
 *         .setSize(2f).setAlpha(0.8f).setOffset(0, 1, 0);
 *     fx.start();
 *     // ... each tick:
 *     fx.tick();
 *     // ... later:
 *     fx.stop();
 * </pre>
 */
@OnlyIn(Dist.CLIENT)
public class EntityEffect {
    private final Entity entity;
    private final Level level;
    private final ResourceLocation fxLocation;
    private @Nullable FX fx;
    private @Nullable EffectRunner runner;
    private boolean started;
    private final DynamicEffectProperties properties = new DynamicEffectProperties();

    public EntityEffect(Entity entity, ResourceLocation fxLocation) {
        this.entity = entity;
        this.level = entity.level();
        this.fxLocation = fxLocation;
    }

    public void start() {
        if (started) return;
        started = true;
        fx = FXHelper.getFX(fxLocation);
        if (fx == null) {
            started = false;
            return;
        }
        runner = new EffectRunner(fx, level, entity, properties);
        runner.emit();
    }

    /** Call each tick to keep size/alpha/position in sync. */
    public void tick() {
        if (!started || runner == null) return;
        var rt = runner.getRuntime();
        if (rt == null) return;
        properties.apply(rt);
    }

    public void stop() {
        if (runner != null) {
            runner.kill();
            runner = null;
        }
        started = false;
    }

    // ── setters ──
    public EntityEffect setSize(float size) { properties.setSize(size); return this; }
    public EntityEffect setAlpha(float alpha) { properties.setAlpha(alpha); return this; }

    public EntityEffect setOffset(Vec3 offset) {
        return setOffset(offset.x, offset.y, offset.z);
    }
    public EntityEffect setOffset(double x, double y, double z) {
        properties.setOffset(x, y, z);
        return this;
    }

    // ── per-effect runtime ──
    private static class EffectRunner extends FXEffectExecutor {
        private final Entity entity;
        private final DynamicEffectProperties properties;

        EffectRunner(FX fx, Level level, Entity entity, DynamicEffectProperties properties) {
            super(fx, level);
            this.entity = entity;
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
            Vec3 pos = properties.offset(entity.getPosition(partialTicks));
            getRuntime().getRoot().updatePos(new Vector3f((float) pos.x, (float) pos.y, (float) pos.z));
        }

        @Override
        public void updateFXObjectTick(IFXObject obj) {
            if (runtime != null && obj == runtime.getRoot() && !entity.isAlive()) kill();
        }

        void emit() {
            if (this.runtime != null) return;
            this.runtime = fx.createRuntime(true);
            Vec3 pos = properties.offset(entity.position());
            this.runtime.getRoot().updatePos(new Vector3f((float) pos.x, (float) pos.y, (float) pos.z));
            properties.apply(this.runtime);
            this.runtime.emmit(this, 0);
        }

        void kill() {
            if (this.runtime != null) {
                this.runtime.destroy(true);
                this.runtime = null;
            }
        }
    }
}
