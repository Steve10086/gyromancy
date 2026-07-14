package com.astune.gyromancy.entity.ball;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.element.ElementStorageManager;
import com.astune.gyromancy.registry.ModAttachments;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
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

    protected void exchangeWithElements(double fireVolumeLoss, double fireEquilibrium,
                                        double firePerVolume, double fireConversionCost,
                                        double manaToVolume) {
        if (level().isClientSide) return;

        float size = getTargetSize();
        List<BlockPos> positions = containedPositions(size);
        if (positions.isEmpty()) return;

        double volume = volume(size);
        double averageFire = positions.stream()
                .mapToLong(pos -> ElementStorageManager.INSTANCE.get(level(), pos).get(ElementType.FIRE))
                .average()
                .orElse(0.0);

        if (averageFire < fireEquilibrium * volume) {
            double lost = Math.min(fireVolumeLoss, volume - volume(SPAWN_SIZE));
            if (lost > 0.0) {
                volume -= lost;
                long firePerBlock = Math.round(Math.max(0.0, firePerVolume * lost - fireConversionCost) / positions.size());
                if (firePerBlock > 0) {
                    for (BlockPos pos : positions) {
                        var current = ElementStorageManager.INSTANCE.get(level(), pos);
                        ElementStorageManager.INSTANCE.set(level(), pos,
                                current.withValue(ElementType.FIRE, current.get(ElementType.FIRE) + firePerBlock));
                    }
                }
            }
        }

        long mana = 0;
        for (BlockPos pos : positions) {
            var current = ElementStorageManager.INSTANCE.get(level(), pos);
            long absorbed = current.get(ElementType.MANA);
            if (absorbed == 0) continue;
            mana += absorbed;
            ElementStorageManager.INSTANCE.set(level(), pos, current.withValue(ElementType.MANA, 0));
        }

        Vec3 velocity = getDeltaMovement();
        setTargetSize((float) sizeForVolume(volume + mana * manaToVolume));
        setDeltaMovement(velocity);
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

    private List<BlockPos> containedPositions(float size) {
        double r = size / 2.0;
        Vec3 center = position().add(0.0, r, 0.0);
        AABB box = new AABB(center.x - r, center.y - r, center.z - r,
                center.x + r, center.y + r, center.z + r);
        List<BlockPos> positions = new ArrayList<>();
        BlockPos.betweenClosedStream(box)
                .map(BlockPos::immutable)
                .filter(pos -> center.distanceToSqr(pos.getCenter()) <= r * r)
                .forEach(positions::add);
        return positions;
    }

    private static double volume(float size) {
        double r = size / 2.0;
        return 4.0 / 3.0 * Math.PI * r * r * r;
    }

    private static double sizeForVolume(double volume) {
        return Math.cbrt(Math.max(volume, volume(SPAWN_SIZE)) * 3.0 / (4.0 * Math.PI)) * 2.0;
    }
}
