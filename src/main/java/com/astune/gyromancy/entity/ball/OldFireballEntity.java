package com.astune.gyromancy.entity.ball;

import com.astune.gyromancy.compile.operator.EntityPayload;
import com.astune.gyromancy.compile.operator.ElementConversionOp;
import com.astune.gyromancy.compile.operator.ElementVolumeOp;
import com.astune.gyromancy.compile.operator.FireProjectileOp;
import com.astune.gyromancy.compile.operator.SmeltOp;
import com.astune.gyromancy.registry.ModEntities;
import com.astune.gyromancy.api.element.ElementType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;



public class OldFireballEntity extends MagicBallEntity {
    private static final EntityDataAccessor<Boolean> DATA_DEBUG =
            SynchedEntityData.defineId(OldFireballEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_AVERAGE_ELEMENT_LEVEL =
            SynchedEntityData.defineId(OldFireballEntity.class, EntityDataSerializers.FLOAT);
    private static final float MIN_SIZE = 0.1F;
    private static final float MERGE_RATE = 0.1F;
    private static final int ELEMENT_EXCHANGE_INTERVAL = 10;
    private static final double FIRE_VOLUME_LOSS = 0.1;
    private static final double FIRE_EQUILIBRIUM = 1000.0;
    private static final double MAX_VOLUME_FIRE_LEVEL = 200.0;
    private static final double MANA_TO_VOLUME = 0.05;

    private Vec3 velocity = Vec3.ZERO;
    private Vec3 acceleration = Vec3.ZERO;

    @Override
    protected List<? extends EntityPayload> defaultPayload() {
        return List.of(
            new ElementVolumeOp(ElementType.FIRE, FireProjectileOp.STORED_MANA_KEY, ELEMENT_EXCHANGE_INTERVAL,
                    FIRE_VOLUME_LOSS, FIRE_EQUILIBRIUM, MAX_VOLUME_FIRE_LEVEL, MANA_TO_VOLUME),
            new ElementConversionOp(ElementType.FIRE, ELEMENT_EXCHANGE_INTERVAL),
            new SmeltOp());
    }

    public OldFireballEntity(EntityType<OldFireballEntity> type, Level level) {
        super(type, level, ElementType.FIRE);
    }

    public OldFireballEntity(Level level, Vec3 pos, Vec3 velocity, Vec3 acceleration, float size) {
        this(ModEntities.OLD_FIREBALL.get(), level);
        this.velocity = velocity;
        this.acceleration = acceleration;
        setBallSize(size);
        setPos(pos);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DEBUG, false);
        builder.define(DATA_AVERAGE_ELEMENT_LEVEL, 0.0F);
    }

    public boolean isDebug() {
        return entityData.get(DATA_DEBUG);
    }

    public void setDebug(boolean debug) {
        entityData.set(DATA_DEBUG, debug);
    }

    public float getSyncedAverageElementLevel() {
        return entityData.get(DATA_AVERAGE_ELEMENT_LEVEL);
    }

    @Override
    protected boolean tickBeforePayload() {
        if (!super.tickBeforePayload()) return false;
        growIntoTargetSize();
        velocity = velocity.add(acceleration);
        setPos(position().add(velocity));
        return true;
    }

    @Override
    protected void tickAfterPayload() {
        entityData.set(DATA_AVERAGE_ELEMENT_LEVEL, (float) getAverageElementLevel());

        float target = getTargetSize();
        double r = target / 2.0;
        AABB box = getBoundingBox();
        BlockPos.betweenClosedStream(box).map(BlockPos::immutable)
                .filter(p -> level().getBlockState(p).isAir() && inSphere(p.getCenter(), r))
                .forEach(this::tryPutFire);
        // 5. Merge with overlapping OldFireballEntity
        merge(target, r, box);

    }

    @Override
    protected Vec3 payloadAcceleration() {
        return acceleration;
    }

    public void setStoredMana(long storedMana) {
        runtimeData().put(FireProjectileOp.STORED_MANA_KEY, storedMana);
    }

    private void tryPutFire(BlockPos pos){
        if(random.nextDouble() < 0.001){
            BlockState fire = BaseFireBlock.getState(level(), pos);
            if (fire.canSurvive(level(), pos))
                level().setBlock(pos, fire, 3);
        }
    }

    private void merge(float target, double r, AABB box) {
        Set<OldFireballEntity> reducedThisTick = new HashSet<>();

        for (OldFireballEntity other : level().getEntitiesOfClass(OldFireballEntity.class, box,
                e -> e != this && e.isAlive() && inSphere(e.position(), r))) {
            float otherTarget = other.getTargetSize();
            if (target < otherTarget || reducedThisTick.contains(other)) continue;

            // Decrease smaller entity's size
            float newOtherTarget = Math.max(0, otherTarget - MERGE_RATE);
            other.setTargetSize(newOtherTarget);

            // Transfer volume to larger entity
            double myR = target / 2.0;
            double otherR = otherTarget / 2.0;
            double newOtherR = newOtherTarget / 2.0;
            double volLost = (4.0 / 3.0) * Math.PI * (otherR * otherR * otherR - newOtherR * newOtherR * newOtherR);
            double newMyR = Math.cbrt(myR * myR * myR + volLost * 3.0 / (4.0 * Math.PI));
            setTargetSize((float)(newMyR * 2.0));

            reducedThisTick.add(other);

            if (newOtherTarget < MIN_SIZE) other.discard();
        }

        for (FireballEntity other : level().getEntitiesOfClass(FireballEntity.class, box,
                e -> e.isAlive() && inSphere(e.position(), r))) {
            float otherTarget = other.getBallSize();
            if (target < otherTarget || reducedThisTick.contains(other)) continue;

            // discard unstable fireball
            other.discard();

            // Transfer volume
            double myR = target / 2.0;
            double otherR = otherTarget / 2.0;
            double volLost = (4.0 / 3.0) * Math.PI * (otherR * otherR * otherR);
            double newMyR = Math.cbrt(myR * myR * myR + volLost * 3.0 / (4.0 * Math.PI));
            setTargetSize((float)(newMyR * 2.0));
        }
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("VelX"))
            velocity = new Vec3(tag.getDouble("VelX"), tag.getDouble("VelY"), tag.getDouble("VelZ"));
        if (tag.contains("AccelX"))
            acceleration = new Vec3(tag.getDouble("AccelX"), tag.getDouble("AccelY"), tag.getDouble("AccelZ"));
        if (tag.contains("StoredMana")) runtimeData().put(FireProjectileOp.STORED_MANA_KEY, tag.getLong("StoredMana"));
        if (tag.contains("AverageElementLevel")) {
            entityData.set(DATA_AVERAGE_ELEMENT_LEVEL, tag.getFloat("AverageElementLevel"));
        }
        setDebug(tag.getBoolean("Debug"));
        refreshDimensions();
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("VelX", velocity.x);
        tag.putDouble("VelY", velocity.y);
        tag.putDouble("VelZ", velocity.z);
        tag.putDouble("AccelX", acceleration.x);
        tag.putDouble("AccelY", acceleration.y);
        tag.putDouble("AccelZ", acceleration.z);
        tag.putLong("StoredMana", ((Number)runtimeData().getOrDefault(FireProjectileOp.STORED_MANA_KEY, 0L)).longValue());
        tag.putFloat("AverageElementLevel", getSyncedAverageElementLevel());
        tag.putBoolean("Debug", isDebug());
    }



}
