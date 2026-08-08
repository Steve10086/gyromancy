package com.astune.gyromancy.entity.ball;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.entity.field.MagicFieldEntity;
import com.astune.gyromancy.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class ManaballEntity extends MagicFieldEntity {
    private static final float DISCARD_SIZE = 0.1f;
    private static final double RESISTANCE_FACTOR = 0.08;
    private static final double RESISTANCE_CONSTANT = 0.002;
    private Vec3 acceleration = Vec3.ZERO;
    private Vec3 pendingVelocity = Vec3.ZERO;
    private Vec3 pendingAcceleration = Vec3.ZERO;
    private boolean launched;

    public ManaballEntity(EntityType<ManaballEntity> type, Level level) {
        super(type, level, ElementType.MANA);
    }

    public ManaballEntity(Level level, Vec3 pos, Vec3 velocity, double arrowSizeSum,
                          double liftDirection, Vec3 acceleration, float size) {
        this(ModEntities.MANABALL.get(), level);
        setFieldSize(size);
        this.pendingVelocity = launchVelocity(velocity, arrowSizeSum, liftDirection);
        this.pendingAcceleration = acceleration;
        this.acceleration = Vec3.ZERO;
        setPos(pos);
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    protected boolean tickBeforePayload() {
        if (!super.tickBeforePayload()) return false;
        growIntoTargetSize();
        if (getFieldSize() <= DISCARD_SIZE) {
            discard();
            return false;
        }

        launchIfReady();

        setDeltaMovement(getDeltaMovement().add(acceleration));

        return true;
    }

    @Override
    protected Vec3 payloadAcceleration() {
        return acceleration;
    }

    private void launchIfReady() {
        if (launched || !isFullyGrown()) return;
        launched = true;
        acceleration = pendingAcceleration;
        setDeltaMovement(pendingVelocity);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("AccelX")) {
            acceleration = new Vec3(tag.getDouble("AccelX"), tag.getDouble("AccelY"), tag.getDouble("AccelZ"));
        }
        if (tag.contains("PendingVelX")) {
            pendingVelocity = new Vec3(tag.getDouble("PendingVelX"), tag.getDouble("PendingVelY"), tag.getDouble("PendingVelZ"));
        }
        if (tag.contains("PendingAccelX")) {
            pendingAcceleration = new Vec3(tag.getDouble("PendingAccelX"), tag.getDouble("PendingAccelY"), tag.getDouble("PendingAccelZ"));
        }
        launched = tag.getBoolean("Launched");
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("AccelX", acceleration.x);
        tag.putDouble("AccelY", acceleration.y);
        tag.putDouble("AccelZ", acceleration.z);
        tag.putDouble("PendingVelX", pendingVelocity.x);
        tag.putDouble("PendingVelY", pendingVelocity.y);
        tag.putDouble("PendingVelZ", pendingVelocity.z);
        tag.putDouble("PendingAccelX", pendingAcceleration.x);
        tag.putDouble("PendingAccelY", pendingAcceleration.y);
        tag.putDouble("PendingAccelZ", pendingAcceleration.z);
        tag.putBoolean("Launched", launched);
    }
}
