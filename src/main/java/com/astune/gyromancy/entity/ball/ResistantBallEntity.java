package com.astune.gyromancy.entity.ball;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public abstract class ResistantBallEntity extends MagicBallEntity {
    private static final double RESISTANCE_FACTOR = 0.08;
    private static final double RESISTANCE_CONSTANT = 0.002;
    private Vec3 pendingVelocity = Vec3.ZERO;
    private boolean launched;

    protected ResistantBallEntity(EntityType<? extends ResistantBallEntity> type, Level level) {
        super(type, level);
    }

    protected void configure(Vec3 pos, Vec3 velocity, double arrowSizeSum, double liftDirection, float size) {
        setBallSize(size);
        setPos(pos);
        pendingVelocity = launchVelocity(velocity, arrowSizeSum, liftDirection);
        setDeltaMovement(pendingVelocity);
    }

    @Override
    public void tick() {
        super.tick();
        growIntoTargetSize();
        if (!launched && isFullyGrown()) {
            launched = true;
        }
        if (launched) moveWithResistance(RESISTANCE_FACTOR, RESISTANCE_CONSTANT);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("PendingVelX")) {
            pendingVelocity = new Vec3(tag.getDouble("PendingVelX"), tag.getDouble("PendingVelY"), tag.getDouble("PendingVelZ"));
        }
        launched = tag.getBoolean("Launched");
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("PendingVelX", pendingVelocity.x);
        tag.putDouble("PendingVelY", pendingVelocity.y);
        tag.putDouble("PendingVelZ", pendingVelocity.z);
        tag.putBoolean("Launched", launched);
    }
}
