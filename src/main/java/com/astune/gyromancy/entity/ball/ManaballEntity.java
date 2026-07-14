package com.astune.gyromancy.entity.ball;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.element.ElementStorageManager;
import com.astune.gyromancy.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class ManaballEntity extends MagicBallEntity {
    private static final float DISCARD_SIZE = 0.1f;
    private float manaExpendFactor = 1f;
    private Vec3 acceleration = Vec3.ZERO;
    private Vec3 pendingVelocity = Vec3.ZERO;
    private Vec3 pendingAcceleration = Vec3.ZERO;
    private boolean launched;
    private float mana = 0;
    private float manaPerBlock = 0;

    public ManaballEntity(EntityType<ManaballEntity> type, Level level) {
        super(type, level);
    }

    public ManaballEntity(Level level, Vec3 pos, Vec3 velocity, Vec3 acceleration, float size) {
        this(ModEntities.MANABALL.get(), level);
        setBallSize(size);
        this.pendingVelocity = velocity;
        this.pendingAcceleration = acceleration;
        this.acceleration = Vec3.ZERO;
        manaExpendFactor *= size;
        setPos(pos);
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void tick() {
        super.tick();
        growIntoTargetSize();
        if (getBallSize() <= DISCARD_SIZE) {
            discard();
            return;
        }

        float target = getTargetSize();
        AABB inflatedBox = getBoundingBox().inflate(target / 2);
        AABB box = getBoundingBox();
        double r = target / 2.0;

        launchIfReady();

        Vec3 velocity = getDeltaMovement();
        setDeltaMovement(velocity.add(acceleration));

        BlockPos.betweenClosedStream(inflatedBox).map(BlockPos::immutable)
                .filter(p -> !inSphere(p.getCenter(), r) && inSphere(p.getCenter(), 2 * r))
                .forEach(this::absorbMana);

        if (mana == 0) return;

        manaPerBlock = mana / BlockPos.betweenClosedStream(box).map(BlockPos::immutable)
                .filter(p -> inSphere(p.getCenter(), r))
                .count();

        BlockPos.betweenClosedStream(box).map(BlockPos::immutable)
                .filter(p -> inSphere(p.getCenter(), r))
                .forEach(this::releaseMana);

        mana = 0;
    }

    private void absorbMana(BlockPos pos) {
        if (level().isClientSide) return;

        var current = ElementStorageManager.INSTANCE.get(level(), pos);
        long absorbed = current.get(ElementType.MANA);
        if (absorbed == 0) return;

        mana += absorbed;
        ElementStorageManager.INSTANCE.set(level(), pos, current.withValue(ElementType.MANA, 0));
    }

    private void releaseMana(BlockPos pos){
        if (level().isClientSide) return;

        var current = ElementStorageManager.INSTANCE.get(level(), pos);
        long mana = (long) ((current.get(ElementType.MANA) + manaPerBlock) * manaExpendFactor);
        ElementStorageManager.INSTANCE.set(level(), pos, current.withValue(ElementType.MANA, mana));
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
        if (tag.contains("AccelX")) {
            acceleration = new Vec3(tag.getDouble("AccelX"), tag.getDouble("AccelY"), tag.getDouble("AccelZ"));
        }
        if (tag.contains("PendingVelX")) {
            pendingVelocity = new Vec3(tag.getDouble("PendingVelX"), tag.getDouble("PendingVelY"), tag.getDouble("PendingVelZ"));
        }
        if (tag.contains("PendingAccelX")) {
            pendingAcceleration = new Vec3(tag.getDouble("PendingAccelX"), tag.getDouble("PendingAccelY"), tag.getDouble("PendingAccelZ"));
        }
        if (tag.contains("Mana")) mana = tag.getFloat("Mana");
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
        tag.putFloat("Mana", mana);
        tag.putBoolean("Launched", launched);
    }
}
