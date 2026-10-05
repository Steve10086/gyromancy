package com.astune.gyromancy.entity.ball;

import com.astune.gyromancy.compile.operator.EntityPayload;
import com.astune.gyromancy.compile.operator.ExplosionOp;
import com.astune.gyromancy.compile.operator.FireProjectileOp;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.network.FireballStateEventPacket;
import com.astune.gyromancy.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class FireballEntity extends MagicBallEntity {
    private static final double MAX_SIZE = 4.5;
    private static final float DEFAULT_EXPLOSION_POWER = 1.5F;
    private boolean clientExplosionPending;
    private boolean lifetimeDiscardPending;
    private boolean terminalVisualEventSent;

    public FireballEntity(EntityType<FireballEntity> type, Level level) {
        super(type, level, ElementType.FIRE);
        initRuntimeData(DEFAULT_EXPLOSION_POWER);
    }

    public FireballEntity(Level level, Vec3 pos, Vec3 velocity, Vec3 acceleration, float size) {
        super(ModEntities.FIREBALL.get(), level, ElementType.FIRE, velocity, acceleration);
        setBallSize(size);
        initRuntimeData(Math.max(1.0F, size));
        this.acceleration = Vec3.ZERO;
        setPos(pos);
    }

    @Override
    protected boolean tickBeforePayload() {
        if (level().isClientSide && clientExplosionPending) return false;
        if (!super.tickBeforePayload()) return false;
        return true;
    }

    /** Classifies a vanilla discard request without changing generic payload operators. */
    public boolean onDiscardRequested() {
        boolean explosionDiscard = !lifetimeDiscardPending
                && (hasImpactThisTick() || getBallSize() > MAX_SIZE);
        if (level().isClientSide && explosionDiscard) {
            clientExplosionPending = true;
            terminalVisualEventSent = true;
            return true;
        }
        if (explosionDiscard) sendTerminalVisualEvent(true);
        return false;
    }

    @Override
    protected void onDiscardLifetimeExpired() {
        lifetimeDiscardPending = true;
        sendTerminalVisualEvent(false);
    }

    private void sendTerminalVisualEvent(boolean explosion) {
        if (terminalVisualEventSent) return;
        terminalVisualEventSent = true;
        FireballStateEventPacket.broadcast(this, explosion);
    }

    private void initRuntimeData(float explosionPower) {
        runtimeData().putIfAbsent(FireProjectileOp.STORED_MANA_KEY, 0L);
        runtimeData().putIfAbsent(FireProjectileOp.OLD_SPAWNED_KEY, false);
        runtimeData().put(ExplosionOp.EXPLOSION_POWER_KEY, explosionPower);
        runtimeData().put(ExplosionOp.MAX_SIZE_KEY, MAX_SIZE);
    }



    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("ExplosionPower")) runtimeData().put(ExplosionOp.EXPLOSION_POWER_KEY, tag.getFloat("ExplosionPower"));
        if (tag.contains("AccelX")) {
            acceleration = new Vec3(tag.getDouble("AccelX"), tag.getDouble("AccelY"), tag.getDouble("AccelZ"));
        }

        if (tag.contains("StoredMana")) runtimeData().put(FireProjectileOp.STORED_MANA_KEY, tag.getLong("StoredMana"));
        runtimeData().put(FireProjectileOp.OLD_SPAWNED_KEY, tag.getBoolean("OldSpawned"));
        runtimeData().put(ExplosionOp.MAX_SIZE_KEY, MAX_SIZE);
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("ExplosionPower", ((Number)runtimeData().getOrDefault(ExplosionOp.EXPLOSION_POWER_KEY, DEFAULT_EXPLOSION_POWER)).floatValue());
        tag.putDouble("AccelX", acceleration.x);
        tag.putDouble("AccelY", acceleration.y);
        tag.putDouble("AccelZ", acceleration.z);
        tag.putLong("StoredMana", ((Number)runtimeData().getOrDefault(FireProjectileOp.STORED_MANA_KEY, 0L)).longValue());
        tag.putBoolean("OldSpawned", Boolean.TRUE.equals(runtimeData().get(FireProjectileOp.OLD_SPAWNED_KEY)));
    }
}
