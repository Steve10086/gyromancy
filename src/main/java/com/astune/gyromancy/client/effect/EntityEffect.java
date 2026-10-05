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
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Attaches a Photon FX to an entity with dynamic size, opacity, and position offset.
 *
 * <pre>
 *     var fx = new EntityEffect(entity, ResourceLocation.parse("gyromancy:srounding_fire"))
 *         .setSize(2f).setAlpha(0.8f).setColor(0xFFFF6600).setOffset(0, 1, 0);
 *     fx.start();
 *     // ... each tick:
 *     fx.tick();
 *     // ... later:
 *     fx.stop();
 * </pre>
 */
@OnlyIn(Dist.CLIENT)
public class EntityEffect {
    private static final Set<EntityEffect> ACTIVE =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private final @Nullable Entity entity;
    private final Level level;
    private final ResourceLocation fxLocation;
    private final Vec3 fixedAnchor;
    private @Nullable FX fx;
    private @Nullable EffectRunner runner;
    private boolean started;
    private boolean detached;
    private final DynamicEffectProperties properties = new DynamicEffectProperties();

    public EntityEffect(Entity entity, ResourceLocation fxLocation) {
        this.entity = entity;
        this.level = entity.level();
        this.fxLocation = fxLocation;
        this.fixedAnchor = entity.position();
    }

    /** Creates an effect at a fixed position, for a state event received after its entity vanished. */
    public EntityEffect(Level level, Vec3 position, ResourceLocation fxLocation) {
        this.entity = null;
        this.level = level;
        this.fxLocation = fxLocation;
        this.fixedAnchor = position;
    }

    public void start() {
        if (started) {
            if (runner != null && runner.isRuntimeValid()) return;
            stop();
        }
        started = true;
        fx = FXHelper.getFX(fxLocation);
        if (fx == null) {
            started = false;
            return;
        }
        runner = new EffectRunner(fx, level, entity, fixedAnchor, properties, this);
        runner.emit();
        ACTIVE.add(this);
    }

    /** Call each tick to keep size/alpha/position in sync. */
    public void tick() {
        if (!started || runner == null) return;
        if (!detached && entity != null && !entity.isAlive()) {
            stop();
            return;
        }
        var rt = runner.getRuntime();
        if (detached) {
            if (rt == null || rt.isFinished()) {
                runner.finishIfDetached();
                return;
            }
            if (!runner.isRuntimeValid()) {
                stop();
                return;
            }
        }
        if (rt == null || !runner.isRuntimeValid()) {
            // ParticleEngine.setLevel() invalidates Photon runtimes without
            // notifying custom executors. Re-emit the same FX on the new
            // client level instead of keeping a dead runtime reference.
            stop();
            start();
            return;
        }
        properties.apply(rt);
    }

    public void stop() {
        ACTIVE.remove(this);
        if (runner != null) {
            runner.kill();
            runner = null;
        }
        started = false;
    }

    /** Sends a Photon state input and freezes the runtime at the supplied entity position. */
    public int sendEventAndDetach(String event, Vec3 entityPosition) {
        if (!started || runner == null || detached) return 0;
        int targets = runner.sendEvent(event);
        if (targets > 0) {
            detached = true;
            runner.detach(properties.offset(entityPosition));
        }
        return targets;
    }

    public boolean isDetached() {
        return detached;
    }

    private void onRuntimeFinished() {
        if (!detached) return;
        ACTIVE.remove(this);
        started = false;
        runner = null;
        fx = null;
    }

    /** Returns whether the Photon runtime is still emitting this effect. */
    public boolean isAlive() {
        return started && runner != null && runner.isAlive();
    }

    /** Stops all custom entity executors before Photon changes level. */
    public static void clearAll() {
        for (EntityEffect effect : new ArrayList<>(ACTIVE)) effect.stop();
        ACTIVE.clear();
    }

    // ── setters ──
    public EntityEffect setSize(float size) { if (!detached) properties.setSize(size); return this; }
    public EntityEffect setAlpha(float alpha) { if (!detached) properties.setAlpha(alpha); return this; }
    public EntityEffect setColor(int argb) { if (!detached) properties.setColor(argb); return this; }

    public EntityEffect setOffset(Vec3 offset) {
        return setOffset(offset.x, offset.y, offset.z);
    }
    public EntityEffect setOffset(double x, double y, double z) {
        if (!detached) properties.setOffset(x, y, z);
        return this;
    }

    public EntityEffect setDir(Vec3 dir){
        if (!detached) properties.setDir(dir);
        return this;
    }

    // ── per-effect runtime ──
    private static class EffectRunner extends FXEffectExecutor {
        private final @Nullable Entity entity;
        private final Vec3 fixedAnchor;
        private final DynamicEffectProperties properties;
        private final EntityEffect owner;
        private @Nullable Vec3 frozenWorldPosition;
        private boolean detached;

        EffectRunner(FX fx, Level level, @Nullable Entity entity, Vec3 fixedAnchor,
                     DynamicEffectProperties properties, EntityEffect owner) {
            super(fx, level);
            this.entity = entity;
            this.fixedAnchor = fixedAnchor;
            this.properties = properties;
            this.owner = owner;
            this.allowMulti = true;
            this.forcedDeath = true;
            setOnFinished(ignored -> owner.onRuntimeFinished());
        }

        @Override
        public void start() {
            // handled by emit()
        }

        @Override
        public void updateFXObjectFrame(IFXObject obj, float partialTicks) {
            if (getRuntime() == null || obj != getRuntime().getRoot()) return;
            properties.apply(getRuntime());
            Vec3 pos = frozenWorldPosition != null
                    ? frozenWorldPosition
                    : properties.offset(entity == null ? fixedAnchor : entity.getPosition(partialTicks));
            getRuntime().getRoot().updatePos(new Vector3f((float) pos.x, (float) pos.y, (float) pos.z));
        }

        @Override
        public void updateFXObjectTick(IFXObject obj) {
            if (runtime == null || obj != runtime.getRoot()) return;
            if (detached) {
                if (runtime.isFinished()) notifyFinished();
            } else if (entity != null && !entity.isAlive()) {
                kill();
            }
        }

        void emit() {
            if (this.runtime != null) return;
            this.runtime = fx.createRuntime(true);
            Vec3 pos = frozenWorldPosition != null
                    ? frozenWorldPosition
                    : properties.offset(entity == null ? fixedAnchor : entity.position());
            this.runtime.getRoot().updatePos(new Vector3f((float) pos.x, (float) pos.y, (float) pos.z));
            properties.apply(this.runtime);
            this.runtime.emmit(this, 0);
        }

        void detach(Vec3 worldPosition) {
            detached = true;
            frozenWorldPosition = worldPosition;
            if (runtime != null) {
                runtime.getRoot().updatePos(new Vector3f(
                        (float) worldPosition.x, (float) worldPosition.y, (float) worldPosition.z));
            }
        }

        void finishIfDetached() {
            if (detached && runtime != null && runtime.isFinished()) notifyFinished();
        }

        boolean isRuntimeValid() {
            return runtime != null && runtime.isValid();
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
