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
    private double averageElementLevel;

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
        refreshDimensions();
    }

    protected void growIntoTargetSize() {
        float cur = getBallSize();
        float targetSize = getTargetBallSize();
        if (cur == targetSize) return;

        float step = Math.max(0.001F, (Math.max(cur, targetSize) - SPAWN_SIZE) * GROWTH_RATE / 100);
        entityData.set(DATA_CURRENT_SIZE, cur < targetSize
                ? Math.min(targetSize, cur + step)
                : Math.max(targetSize, cur - step));
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

    protected double getAverageElementLevel() {
        return averageElementLevel;
    }

    protected long exchangeWithElements(double fireVolumeLoss, double fireEquilibrium,
                                        double maxVolumeFireLevel, double firePerVolume,
                                        double fireConversionCost, double manaToVolume,
                                        long storedMana) {
        return exchangeWithElements(ElementType.FIRE, fireVolumeLoss, fireEquilibrium, maxVolumeFireLevel,
                firePerVolume, fireConversionCost, manaToVolume, storedMana);
    }

    protected long exchangeWithElements(ElementType element, double fireVolumeLoss, double fireEquilibrium,
                                        double maxVolumeFireLevel, double firePerVolume,
                                        double fireConversionCost, double manaToVolume,
                                        long storedMana) {
        if (level().isClientSide) return storedMana;

        float size = getTargetSize();
        List<BlockPos> positions = containedPositions(size);
        if (positions.isEmpty()) return storedMana;

        double volume = volume(size);
        double averageFire = positions.stream()
                .mapToLong(pos -> ElementStorageManager.INSTANCE.get(level(), pos).get(element))
                .average()
                .orElse(0.0);

        double naturalFire = 0.0;
        if (averageFire < fireEquilibrium * volume) {
            double lost = Math.min(volume * 0.005 + fireVolumeLoss, volume - volume(SPAWN_SIZE));
            if (lost > 0.0) {
                volume -= lost;
                naturalFire = Math.max(0.0, firePerVolume * lost - fireConversionCost);
            }
        }

        double maxManaVolumeGain = volume * 0.05 + fireVolumeLoss;
        long manaBudget = manaToVolume <= 0.0 ? 0 : (long)Math.ceil(maxManaVolumeGain / manaToVolume);
        if (maxVolumeFireLevel > 0.0 && manaToVolume > 0.0) {
            double maxVolumeByFire = averageFire / maxVolumeFireLevel;
            double allowedVolumeGain = Math.max(0.0, maxVolumeByFire - volume);
            manaBudget = Math.min(manaBudget, (long)Math.floor(allowedVolumeGain / manaToVolume));
        }
        storedMana = Math.min(storedMana, manaBudget);
        storedMana += drainManaForGrowth(positions, Math.max(0L, manaBudget - storedMana));
        long directMana = drainMana(positions);

        long firePerBlock = (long)Math.floor((naturalFire + directMana) / positions.size());
        averageElementLevel = averageFire + firePerBlock;
        if (firePerBlock > 0) {
            for (BlockPos pos : positions) {
                var current = ElementStorageManager.INSTANCE.get(level(), pos);
                ElementStorageManager.INSTANCE.set(level(), pos,
                        current.withValue(element, current.get(element) + firePerBlock));
            }
        }

        long manaToBurn = Math.min(storedMana, manaBudget);
        storedMana -= manaToBurn;

        Vec3 velocity = getDeltaMovement();
        setTargetSize((float) sizeForVolume(volume + manaToBurn * manaToVolume));
        setDeltaMovement(velocity);
        return storedMana;
    }

    private long drainManaForGrowth(List<BlockPos> positions, long needed) {
        long drained = 0L;
        for (BlockPos pos : positions) {
            if (drained >= needed) break;
            var current = ElementStorageManager.INSTANCE.get(level(), pos);
            long currentMana = current.get(ElementType.MANA);
            long absorbed = Math.min(currentMana, needed - drained);
            if (absorbed == 0) continue;
            drained += absorbed;
            ElementStorageManager.INSTANCE.set(level(), pos, current.withValue(ElementType.MANA, currentMana - absorbed));
        }
        return drained;
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_TARGET_SIZE.equals(key)) {
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

    protected List<BlockPos> containedPositions(float size) {
        double r = size / 2.0;
        Vec3 center = position().add(0.0, r, 0.0);
        AABB box = new AABB(center.x - r, center.y - r, center.z - r,
                center.x + r, center.y + r, center.z + r);
        List<BlockPos> positions = new ArrayList<>();
        BlockPos.betweenClosedStream(box)
                .map(BlockPos::immutable)
                .filter(pos -> center.distanceToSqr(pos.getCenter()) <= r * r)
                .forEach(positions::add);
        if (positions.isEmpty()) positions.add(BlockPos.containing(center));
        return positions;
    }

    protected void moveWithResistance(double factor, double constant) {
        Vec3 velocity = getDeltaMovement();
        setPos(position().add(velocity));
        double speed = velocity.length();
        if (speed == 0.0) return;

        double loss = Math.max(0.0, factor * speed - constant);
        setDeltaMovement(velocity.scale(Math.max(0.0, speed - loss) / speed));
    }

    protected long consumeElement(List<BlockPos> positions, ElementType type, long amount) {
        long consumed = 0L;
        for (BlockPos pos : positions) {
            if (consumed >= amount) break;
            var current = ElementStorageManager.INSTANCE.get(level(), pos);
            long available = Math.max(0L, current.get(type));
            long taken = Math.min(available, amount - consumed);
            if (taken == 0L) continue;
            consumed += taken;
            ElementStorageManager.INSTANCE.set(level(), pos,
                    current.withValue(type, current.get(type) - taken));
        }
        return consumed;
    }

    private long drainMana(List<BlockPos> positions) {
        long drained = 0L;
        for (BlockPos pos : positions) {
            var current = ElementStorageManager.INSTANCE.get(level(), pos);
            long mana = current.get(ElementType.MANA);
            if (mana == 0L) continue;
            drained += mana;
            ElementStorageManager.INSTANCE.set(level(), pos, current.withValue(ElementType.MANA, 0L));
        }
        return drained;
    }

    private static double volume(float size) {
        double r = size / 2.0;
        return 4.0 / 3.0 * Math.PI * r * r * r;
    }

    private static double sizeForVolume(double volume) {
        return Math.cbrt(Math.max(volume, volume(SPAWN_SIZE)) * 3.0 / (4.0 * Math.PI)) * 2.0;
    }
}
