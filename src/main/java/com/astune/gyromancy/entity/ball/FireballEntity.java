package com.astune.gyromancy.entity.ball;

import com.astune.gyromancy.registry.ModEntities;
import com.astune.gyromancy.symbol.CenterSymbol;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class FireballEntity extends MagicBallEntity {
    private static final int DEFAULT_LIFETIME = 500;
    private float explosionPower = 1.5F;
    private int lifetime = DEFAULT_LIFETIME;
    private Vec3 acceleration = Vec3.ZERO;
    private Vec3 pendingVelocity = Vec3.ZERO;
    private Vec3 pendingAcceleration = Vec3.ZERO;
    private boolean launched;
    private boolean oldSpawned;

    public FireballEntity(EntityType<FireballEntity> type, Level level) {
        super(type, level);
    }

    public FireballEntity(Level level, Vec3 pos, Vec3 velocity, Vec3 acceleration, float size) {
        this(ModEntities.FIREBALL.get(), level);
        setBallSize(size);
        this.explosionPower = Math.max(1.0F, size);
        this.pendingVelocity = velocity;
        this.pendingAcceleration = acceleration;
        this.acceleration = Vec3.ZERO;
        setPos(pos);
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void tick() {
        super.tick();
        growIntoTargetSize();
        if (tickCount > lifetime) {
            discard();
            return;
        }
        if (!level().isClientSide && tickCount > lifetime * 0.9 && !oldSpawned) {
            OldFireballEntity oldFireball = new OldFireballEntity(level(), position(), getDeltaMovement(), acceleration, getTargetBallSize());
            bindGeneratedEntity(oldFireball, CenterSymbol.OLD_FIREBALL_KEY);
            level().addFreshEntity(oldFireball);
            oldSpawned = true;
        }
        if (!isFullyGrown()) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        launchIfReady();

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

        burnEntitiesInPath(velocity);
        setDeltaMovement(velocity.add(acceleration));
    }

    private void burnEntitiesInPath(Vec3 velocity) {
        var searchBox = getBoundingBox().expandTowards(velocity).inflate(0.3);
        level().getEntitiesOfClass(Entity.class, searchBox, e -> e != this).forEach(entity -> {
            if (entity instanceof ItemEntity || entity instanceof AbstractArrow) {
                entity.setRemainingFireTicks(200);
            }
        });
    }

    private void launchIfReady() {
        if (launched) return;
        launched = true;
        acceleration = pendingAcceleration;
        setDeltaMovement(pendingVelocity);
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

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("ExplosionPower")) explosionPower = tag.getFloat("ExplosionPower");
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
        launched = tag.getBoolean("Launched");
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("ExplosionPower", explosionPower);
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
        tag.putBoolean("Launched", launched);
    }
}
