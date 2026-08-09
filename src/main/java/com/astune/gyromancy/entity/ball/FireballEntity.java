package com.astune.gyromancy.entity.ball;

import com.astune.gyromancy.compile.operator.EntityPayload;
import com.astune.gyromancy.compile.operator.ExplosionOp;
import com.astune.gyromancy.compile.operator.FireProjectileOp;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class FireballEntity extends MagicBallEntity {
    private static final int DEFAULT_LIFETIME = 500;
    private static final double MAX_SIZE = 4.5;
    private static final float DEFAULT_EXPLOSION_POWER = 1.5F;
    private int lifetime = DEFAULT_LIFETIME;


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

    public void setLifetime(int lifetime) {
        this.lifetime = lifetime;
        runtimeData().put(FireProjectileOp.LIFETIME_KEY, lifetime);
    }


    @Override
    protected boolean tickBeforePayload() {
        if (!super.tickBeforePayload()) return false;
        if (tickCount > lifetime) {
            discard();
            return false;
        }

        runtimeData().put(FireProjectileOp.LIFETIME_KEY, lifetime);
        return true;
    }

    private void initRuntimeData(float explosionPower) {
        runtimeData().putIfAbsent(FireProjectileOp.STORED_MANA_KEY, 0L);
        runtimeData().putIfAbsent(FireProjectileOp.OLD_SPAWNED_KEY, false);
        runtimeData().put(FireProjectileOp.LIFETIME_KEY, lifetime);
        runtimeData().put(ExplosionOp.EXPLOSION_POWER_KEY, explosionPower);
        runtimeData().put(ExplosionOp.MAX_SIZE_KEY, MAX_SIZE);
    }



    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("ExplosionPower")) runtimeData().put(ExplosionOp.EXPLOSION_POWER_KEY, tag.getFloat("ExplosionPower"));
        if (tag.contains("Lifetime")) lifetime = tag.getInt("Lifetime");
        if (tag.contains("AccelX")) {
            acceleration = new Vec3(tag.getDouble("AccelX"), tag.getDouble("AccelY"), tag.getDouble("AccelZ"));
        }

        if (tag.contains("StoredMana")) runtimeData().put(FireProjectileOp.STORED_MANA_KEY, tag.getLong("StoredMana"));
        runtimeData().put(FireProjectileOp.LIFETIME_KEY, lifetime);
        runtimeData().put(FireProjectileOp.OLD_SPAWNED_KEY, tag.getBoolean("OldSpawned"));
        runtimeData().put(ExplosionOp.MAX_SIZE_KEY, MAX_SIZE);
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("ExplosionPower", ((Number)runtimeData().getOrDefault(ExplosionOp.EXPLOSION_POWER_KEY, DEFAULT_EXPLOSION_POWER)).floatValue());
        tag.putInt("Lifetime", lifetime);
        tag.putDouble("AccelX", acceleration.x);
        tag.putDouble("AccelY", acceleration.y);
        tag.putDouble("AccelZ", acceleration.z);
        tag.putLong("StoredMana", ((Number)runtimeData().getOrDefault(FireProjectileOp.STORED_MANA_KEY, 0L)).longValue());
        tag.putBoolean("OldSpawned", Boolean.TRUE.equals(runtimeData().get(FireProjectileOp.OLD_SPAWNED_KEY)));
    }
}
