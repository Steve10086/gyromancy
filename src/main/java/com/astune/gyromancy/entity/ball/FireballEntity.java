package com.astune.gyromancy.entity.ball;

import com.astune.gyromancy.compile.operator.EntityTickContext;
import com.astune.gyromancy.compile.operator.ExplosionOp;
import com.astune.gyromancy.compile.operator.FireballOp;
import com.astune.gyromancy.compile.operator.OnEntityTickOp;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FireballEntity extends MagicBallEntity {
    private static final int DEFAULT_LIFETIME = 500;
    private static final double MAX_SIZE = 4.5;
    private static final float DEFAULT_EXPLOSION_POWER = 1.5F;
    private int lifetime = DEFAULT_LIFETIME;
    private Vec3 pendingVelocity = Vec3.ZERO;
    private Vec3 pendingAcceleration = Vec3.ZERO;
    private boolean launched;
    private final Map<String, Object> runtimeData = new HashMap<>();
    private List<OnEntityTickOp> payload = new ArrayList<>(FireballOp.defaultPayload());
    private static final String PAYLOAD_KEY = "Payload";

    public FireballEntity(EntityType<FireballEntity> type, Level level) {
        super(type, level, ElementType.FIRE);
        initRuntimeData(DEFAULT_EXPLOSION_POWER);
    }

    public FireballEntity(Level level, Vec3 pos, Vec3 velocity, double arrowSizeSum,
                          double liftDirection, Vec3 acceleration, float size) {
        this(ModEntities.FIREBALL.get(), level);
        setBallSize(size);
        initRuntimeData(Math.max(1.0F, size));
        this.pendingVelocity = launchVelocity(velocity, arrowSizeSum, liftDirection);
        this.pendingAcceleration = acceleration;
        this.acceleration = Vec3.ZERO;
        setPos(pos);
        setDeltaMovement(Vec3.ZERO);
    }

    public void setPayload(List<OnEntityTickOp> payload) {
        this.payload = new ArrayList<>(payload);
    }

    public void setLifetime(int lifetime) {
        this.lifetime = lifetime;
        runtimeData.put(FireballOp.LIFETIME_KEY, lifetime);
    }

    @Override
    public void tick() {
        super.tick();
        growIntoTargetSize();
        if (tickCount > lifetime) {
            discard();
            return;
        }
        if (!launched && !isFullyGrown()) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        launchIfReady();

        runtimeData.put(FireballOp.LIFETIME_KEY, lifetime);
        EntityTickContext ctx = EntityTickContext.from(this, runtimeData, acceleration);
        payload.forEach(op -> op.onEntityTick(ctx));
        if (!isAlive()) return;
    }

    private void initRuntimeData(float explosionPower) {
        runtimeData.putIfAbsent(FireballOp.STORED_MANA_KEY, 0L);
        runtimeData.putIfAbsent(FireballOp.OLD_SPAWNED_KEY, false);
        runtimeData.put(FireballOp.LIFETIME_KEY, lifetime);
        runtimeData.put(ExplosionOp.EXPLOSION_POWER_KEY, explosionPower);
        runtimeData.put(ExplosionOp.MAX_SIZE_KEY, MAX_SIZE);
    }

    private void launchIfReady() {
        if (launched) return;
        launched = true;
        acceleration = pendingAcceleration;
        setDeltaMovement(pendingVelocity);
    }



    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("ExplosionPower")) runtimeData.put(ExplosionOp.EXPLOSION_POWER_KEY, tag.getFloat("ExplosionPower"));
        if (tag.contains("Lifetime")) lifetime = tag.getInt("Lifetime");
        if (tag.contains("AccelX")) {
            acceleration = new Vec3(tag.getDouble("AccelX"), tag.getDouble("AccelY"), tag.getDouble("AccelZ"));
        }
        if (tag.contains("PendingVelX")) {
            pendingVelocity = new Vec3(tag.getDouble("PendingVelX"), tag.getDouble("PendingVelY"), tag.getDouble("PendingVelZ"));
        }
        if (tag.contains("PendingAccelX")) {
            pendingAcceleration = new Vec3(tag.getDouble("PendingAccelX"), tag.getDouble("PendingAccelY"), tag.getDouble("PendingAccelZ"));
        }
        if (tag.contains("StoredMana")) runtimeData.put(FireballOp.STORED_MANA_KEY, tag.getLong("StoredMana"));
        payload = new ArrayList<>(OnEntityTickOp.loadPayloadList(tag, PAYLOAD_KEY, FireballOp.defaultPayload()));
        runtimeData.put(FireballOp.LIFETIME_KEY, lifetime);
        runtimeData.put(FireballOp.OLD_SPAWNED_KEY, tag.getBoolean("OldSpawned"));
        runtimeData.put(ExplosionOp.MAX_SIZE_KEY, MAX_SIZE);
        launched = tag.getBoolean("Launched");
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("ExplosionPower", ((Number)runtimeData.getOrDefault(ExplosionOp.EXPLOSION_POWER_KEY, DEFAULT_EXPLOSION_POWER)).floatValue());
        tag.putInt("Lifetime", lifetime);
        tag.putDouble("AccelX", acceleration.x);
        tag.putDouble("AccelY", acceleration.y);
        tag.putDouble("AccelZ", acceleration.z);
        tag.putDouble("PendingVelX", pendingVelocity.x);
        tag.putDouble("PendingVelY", pendingVelocity.y);
        tag.putDouble("PendingVelZ", pendingVelocity.z);
        tag.putDouble("PendingAccelX", pendingAcceleration.x);
        tag.putDouble("PendingAccelY", pendingAcceleration.y);
        tag.putDouble("PendingAccelZ", pendingAcceleration.z);
        tag.putLong("StoredMana", ((Number)runtimeData.getOrDefault(FireballOp.STORED_MANA_KEY, 0L)).longValue());
        tag.put(PAYLOAD_KEY, OnEntityTickOp.savePayloadList(payload));
        tag.putBoolean("OldSpawned", Boolean.TRUE.equals(runtimeData.get(FireballOp.OLD_SPAWNED_KEY)));
        tag.putBoolean("Launched", launched);
    }
}
