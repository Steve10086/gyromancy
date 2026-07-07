package com.astune.gyromancy.entity;

import com.astune.gyromancy.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class FireballEntity extends Entity implements ItemSupplier {
    private static final EntityDataAccessor<Float> DATA_TARGET_SIZE =
            SynchedEntityData.defineId(FireballEntity.class, EntityDataSerializers.FLOAT);
    private static final int DEFAULT_LIFETIME = 1000;
    private static final float SPAWN_SIZE = 0.5F;
    private static final int GROWTH_RATE = 2;
    private float explosionPower = 1.5F;
    private int lifetime = DEFAULT_LIFETIME;
    private Vec3 acceleration = Vec3.ZERO;
    private float currentSize = SPAWN_SIZE;

    public FireballEntity(EntityType<FireballEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public FireballEntity(Level level, Vec3 pos, Vec3 velocity, Vec3 acceleration, float size) {
        this(ModEntities.FIREBALL.get(), level);
        setFireballSize(size);
        this.explosionPower = Math.max(1.0F, size);
        this.acceleration = acceleration;
        setPos(pos);
        setDeltaMovement(velocity);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        builder.define(DATA_TARGET_SIZE, SPAWN_SIZE);
    }

    @Override
    public void tick() {
        super.tick();
        growIntoTargetSize();
        if (tickCount > lifetime) {
            discard();
            return;
        }

        Vec3 velocity = getDeltaMovement();
        Vec3 start = position();
        Vec3 end = start.add(velocity);
        HitResult blockHit = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation();
        }

        setPos(end);
        if (blockHit.getType() != HitResult.Type.MISS || hitLivingEntity(velocity)) {
            explode();
            return;
        }

        setDeltaMovement(velocity.add(acceleration));
    }

    private boolean hitLivingEntity(Vec3 velocity) {
        var searchBox = getBoundingBox().expandTowards(velocity).inflate(0.1);
        return !level().getEntitiesOfClass(LivingEntity.class, searchBox,
                LivingEntity::isAlive).isEmpty();
    }

    private void explode() {
        if (!level().isClientSide) {
            level().explode(this, getX(), getY(), getZ(), explosionPower, true, Level.ExplosionInteraction.MOB);
            igniteNearbyBlocks();
        }
        discard();
    }

    private void igniteNearbyBlocks() {
        int radius = Math.max(1, (int)Math.ceil(explosionPower));
        BlockPos center = blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (!level().isEmptyBlock(pos)) continue;
            BlockState fire = BaseFireBlock.getState(level(), pos);
            if (fire.canSurvive(level(), pos)) level().setBlock(pos, fire, 3);
        }
    }

    public float getFireballSize() {
        return currentSize;
    }

    public float getTargetFireballSize() {
        return entityData.get(DATA_TARGET_SIZE);
    }

    public void setFireballSize(float size) {
        float targetSize = Math.max(SPAWN_SIZE, size);
        entityData.set(DATA_TARGET_SIZE, targetSize);
        currentSize = Math.min(currentSize, targetSize);
        refreshDimensions();
    }

    private void growIntoTargetSize() {
        float targetSize = getTargetFireballSize();
        if (currentSize >= targetSize) return;

        currentSize = Math.min(targetSize, currentSize + (targetSize - SPAWN_SIZE) * GROWTH_RATE / 100);
        refreshDimensions();
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_TARGET_SIZE.equals(key)) {
            currentSize = Math.min(currentSize, getTargetFireballSize());
            refreshDimensions();
        }
    }

    @Override
    public @NotNull EntityDimensions getDimensions(@NotNull Pose pose) {
        float size = getFireballSize();
        return EntityDimensions.scalable(size, size);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        if (tag.contains("Size")) setFireballSize(tag.getFloat("Size"));
        if (tag.contains("CurrentSize")) currentSize = tag.getFloat("CurrentSize");
        if (tag.contains("ExplosionPower")) explosionPower = tag.getFloat("ExplosionPower");
        if (tag.contains("Lifetime")) lifetime = tag.getInt("Lifetime");
        if (tag.contains("AccelX")) {
            acceleration = new Vec3(tag.getDouble("AccelX"), tag.getDouble("AccelY"), tag.getDouble("AccelZ"));
        }
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        tag.putFloat("Size", getTargetFireballSize());
        tag.putFloat("CurrentSize", currentSize);
        tag.putFloat("ExplosionPower", explosionPower);
        tag.putInt("Lifetime", lifetime);
        tag.putDouble("AccelX", acceleration.x);
        tag.putDouble("AccelY", acceleration.y);
        tag.putDouble("AccelZ", acceleration.z);
    }

    @Override
    public @NotNull ItemStack getItem() {
        return new ItemStack(Items.FIRE_CHARGE);
    }
}
