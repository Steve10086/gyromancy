package com.astune.gyromancy.entity.ball;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.registry.ModAttachments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public abstract class MagicBallEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_TARGET_SIZE =
            SynchedEntityData.defineId(MagicBallEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CURRENT_SIZE =
            SynchedEntityData.defineId(MagicBallEntity.class, EntityDataSerializers.FLOAT);
    protected static final float SPAWN_SIZE = 0.1F;
    private static final int GROWTH_RATE = 2;
    private UUID boundArrayId;

    public MagicBallEntity(EntityType<? extends MagicBallEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        builder.define(DATA_TARGET_SIZE, SPAWN_SIZE);
        builder.define(DATA_CURRENT_SIZE, SPAWN_SIZE);
    }

    public float getBallSize() {
        return entityData.get(DATA_CURRENT_SIZE);
    }

    public float getTargetBallSize() {
        return entityData.get(DATA_TARGET_SIZE);
    }

    public float getTargetSize() {
        return getTargetBallSize();
    }

    public boolean isFullyGrown() {
        return getBallSize() >= getTargetBallSize();
    }

    public int getGrowthTicks() {
        return getTargetBallSize() <= SPAWN_SIZE ? 0 : (int)Math.ceil(100.0F / GROWTH_RATE);
    }

    public void setBallSize(float size) {
        setTargetSize(size);
    }

    protected void setTargetSize(float size) {
        float targetSize = Math.max(SPAWN_SIZE, size);
        entityData.set(DATA_TARGET_SIZE, targetSize);
        entityData.set(DATA_CURRENT_SIZE, Math.min(entityData.get(DATA_CURRENT_SIZE), targetSize));
        refreshDimensions();
    }

    protected void growIntoTargetSize() {
        float cur = getBallSize();
        float targetSize = getTargetBallSize();
        if (cur >= targetSize) return;

        entityData.set(DATA_CURRENT_SIZE, Math.min(targetSize, cur + (targetSize - SPAWN_SIZE) * GROWTH_RATE / 100));
        refreshDimensions();
    }

    public void bindToArray(UUID arrayId) {
        this.boundArrayId = arrayId;
    }

    protected void bindGeneratedEntity(MagicBallEntity entity, String scratchKey) {
        if (boundArrayId == null || !(level() instanceof ServerLevel serverLevel)) return;

        entity.bindToArray(boundArrayId);
        serverLevel.getData(ModAttachments.ARRAY_MANAGER)
                .setArrayScratchValue(boundArrayId, scratchKey, ArrayObject.EntityRef.of(entity));
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_TARGET_SIZE.equals(key)) {
            entityData.set(DATA_CURRENT_SIZE, Math.min(getBallSize(), getTargetBallSize()));
            refreshDimensions();
        } else if (DATA_CURRENT_SIZE.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public @NotNull EntityDimensions getDimensions(@NotNull Pose pose) {
        float size = getBallSize();
        return EntityDimensions.scalable(size, size);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        if (tag.contains("Size")) setBallSize(tag.getFloat("Size"));
        if (tag.contains("CurrentSize")) entityData.set(DATA_CURRENT_SIZE, tag.getFloat("CurrentSize"));
        if (tag.hasUUID("ArrayId")) boundArrayId = tag.getUUID("ArrayId");
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        tag.putFloat("Size", getTargetBallSize());
        tag.putFloat("CurrentSize", getBallSize());
        if (boundArrayId != null) tag.putUUID("ArrayId", boundArrayId);
    }

    protected boolean inSphere(Vec3 target, double radius) {
        return position().add(0, radius, 0).distanceToSqr(target) <= radius * radius;
    }
}
