package com.astune.gyromancy.entity.field;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.compile.operator.EntityPayload;
import com.astune.gyromancy.compile.operator.EntityTickContext;
import com.astune.gyromancy.compile.operator.ElementVolumeOp;
import com.astune.gyromancy.element.ElementStorageManager;
import com.astune.gyromancy.entity.ArrayRelativePosition;
import com.astune.gyromancy.entity.MagicEntity;
import com.astune.gyromancy.registry.ModAttachments;
import com.astune.gyromancy.util.MagicBallGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Base entity for magic effects that occupy a persistent volume.
 *
 * <p>Unlike a {@code MagicBallEntity}, a field is fully sized as soon as it
 * is created. Its motion is damped by a fixed deceleration and is capped at
 * the speed it had when it was created.</p>
 */
public abstract class MagicFieldEntity extends MagicEntity {
    private static final EntityDataAccessor<Float> DATA_TARGET_SIZE =
            SynchedEntityData.defineId(MagicFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CURRENT_SIZE =
            SynchedEntityData.defineId(MagicFieldEntity.class, EntityDataSerializers.FLOAT);

    protected static final float SPAWN_SIZE = 0.1F;
    private static final int GROWTH_RATE = 2;
    private static final double RESISTANCE_ACCELERATION = 0.002D;
    private static final double SPEED_EPSILON = 1.0E-12;
    private static final int FOLLOW_TICKS = 20;

    private UUID boundArrayId;
    private ArrayRelativePosition arrayRelativePosition;
    private final ElementType targetElement;
    private double averageElementLevel;
    private boolean impactThisTick;
    protected Vec3 acceleration = Vec3.ZERO;
    private double initialSpeed;

    protected MagicFieldEntity(EntityType<?> type, Level level) {
        this(type, level, ElementType.MANA);
    }

    protected MagicFieldEntity(EntityType<?> type,
                               Level level, ElementType targetElement) {
        super(type, level);
        this.targetElement = targetElement;
        this.noPhysics = true;
    }

    protected MagicFieldEntity(EntityType<?> type,
                               Level level, ElementType targetElement,
                               Vec3 velocity, Vec3 acceleration) {
        this(type, level, targetElement);
        configureMotion(velocity, acceleration);
    }

    /**
     * Configures the field's initial motion. Fields do not have a launch
     * phase, so their initial velocity is active immediately.
     */
    protected final void configureMotion(Vec3 velocity, Vec3 acceleration) {
        this.initialSpeed = Math.max(0.0, velocity.length());
        this.acceleration = acceleration;
        this.velocityThisTick = velocity;
        setDeltaMovement(velocity);
    }

    @Override
    protected boolean tickBeforePayload() {
        updateArrayRelativePosition();
        growIntoTargetSize();

        Vec3 velocity = cappedSpeed(velocityThisTick);
        setDeltaMovement(velocity);

        Vec3 start = position();
        Vec3 end = start.add(velocity);
        HitResult blockHit = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation();
        }

        setPos(end);
        impactThisTick = blockHit.getType() != HitResult.Type.MISS || hitLivingEntity(velocity);
        setDeltaMovement(cappedSpeed(applyAccelerationAndResistance(velocity)));

        return true;
    }

    @Override
    protected void tickAfterPayload() {
        // Payloads may add momentum after the base motion update; enforce the
        // field's initial-speed limit for the velocity that leaves this tick.
        setDeltaMovement(cappedSpeed(getDeltaMovement()));
    }

    @Override
    protected EntityTickContext payloadContext(Map<String, Object> runtimeData) {
        return EntityTickContext.from(this, runtimeData, payloadAcceleration());
    }

    @Override
    protected List<? extends EntityPayload> defaultPayload() {
        return List.of(ElementVolumeOp.stability(targetElement));
    }

    protected Vec3 payloadAcceleration() {
        return acceleration;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TARGET_SIZE, SPAWN_SIZE);
        builder.define(DATA_CURRENT_SIZE, SPAWN_SIZE);
    }

    public boolean hasImpactThisTick() {
        return impactThisTick;
    }

    private boolean hitLivingEntity(Vec3 velocity) {
        AABB searchBox = getBoundingBox().expandTowards(velocity).inflate(0.1);
        return !level().getEntitiesOfClass(LivingEntity.class, searchBox,
                LivingEntity::isAlive).isEmpty();
    }

    public float getFieldSize() {
        return entityData.get(DATA_CURRENT_SIZE);
    }

    public float getCurrentSize() {
        return getFieldSize();
    }

    public float getTargetFieldSize() {
        return entityData.get(DATA_TARGET_SIZE);
    }

    public float getTargetSize() {
        return getTargetFieldSize();
    }

    public boolean isFullyGrown() {
        return getFieldSize() >= getTargetFieldSize();
    }

    public int getGrowthTicks() {
        return getTargetFieldSize() <= SPAWN_SIZE ? 0 : (int) Math.ceil(100.0F / GROWTH_RATE);
    }

    public void setFieldSize(float size) {
        setInitialSize(size);
    }

    public void setTargetVolume(double volume) {
        Vec3 velocity = getDeltaMovement();
        setTargetSize((float) MagicBallGeometry.sizeForVolume(volume, SPAWN_SIZE));
        setDeltaMovement(velocity);
    }

    /** Changes the target size; the current size catches up during ticks. */
    protected void setTargetSize(float size) {
        float targetSize = Math.max(SPAWN_SIZE, size);
        entityData.set(DATA_TARGET_SIZE, targetSize);
        refreshDimensions();
    }

    /** Sets both sizes for the field's initial appearance. */
    private void setInitialSize(float size) {
        float targetSize = Math.max(SPAWN_SIZE, size);
        entityData.set(DATA_TARGET_SIZE, targetSize);
        entityData.set(DATA_CURRENT_SIZE, targetSize);
        refreshDimensions();
    }

    protected Vec3 launchVelocity(Vec3 velocity, double arrowSizeSum, double liftDirection) {
        double speed = velocity.length();
        double lift = ((arrowSizeSum - speed) + 0.2 * speed) * liftDirection;
        return velocity.add(0.0, lift, 0.0);
    }

    protected void growIntoTargetSize() {
        float currentSize = getFieldSize();
        float targetSize = getTargetFieldSize();
        if (currentSize == targetSize) return;

        float step = Math.max(0.001F,
                (Math.max(currentSize, targetSize) - SPAWN_SIZE) * GROWTH_RATE / 100.0F);
        entityData.set(DATA_CURRENT_SIZE, currentSize < targetSize
                ? Math.min(targetSize, currentSize + step)
                : Math.max(targetSize, currentSize - step));
        refreshDimensions();
    }

    private Vec3 applyAccelerationAndResistance(Vec3 velocity) {
        Vec3 accelerated = velocity.add(acceleration);
        double speed = accelerated.length();
        if (speed <= SPEED_EPSILON) return Vec3.ZERO;

        // A fixed negative acceleration in the direction opposite to motion.
        double resistedSpeed = Math.max(0.0, speed - RESISTANCE_ACCELERATION);
        return accelerated.scale(resistedSpeed / speed);
    }

    private Vec3 cappedSpeed(Vec3 velocity) {
        double speed = velocity.length();
        if (speed <= SPEED_EPSILON || speed <= initialSpeed) return velocity;
        if (initialSpeed <= SPEED_EPSILON) return Vec3.ZERO;
        return velocity.scale(initialSpeed / speed);
    }

    public void bindToArray(UUID arrayId) {
        this.boundArrayId = arrayId;
        captureArrayRelativePosition();
        bindPayloadToArray(arrayId);
    }

    private void updateArrayRelativePosition() {
        if (!(level() instanceof ServerLevel serverLevel) || boundArrayId == null) return;
        ArrayObject array = serverLevel.getData(ModAttachments.ARRAY_MANAGER)
                .getArrayObj(boundArrayId);
        if (array == null) {
            arrayRelativePosition = null;
            return;
        }
        if (!shouldFollow(serverLevel.getGameTime(), array.compilationEffectEndTick(),
                getDeltaMovement(), payloadAcceleration())) {
            arrayRelativePosition = null;
            return;
        }
        if (arrayRelativePosition == null) {
            arrayRelativePosition = ArrayRelativePosition.capture(
                    position(), array.rootCircleGlyph().center(),
                    array.rootCircleGlyph().surface());
        }
        setPos(arrayRelativePosition.resolve(
                array.rootCircleGlyph().center(), array.rootCircleGlyph().surface()));
    }

    private static boolean shouldFollow(long gameTime, long compilationEffectEndTick,
                                        Vec3 velocity, Vec3 acceleration) {
        long activatedTick = compilationEffectEndTick - ArrayObject.COMPILATION_EFFECT_TICKS;
        long age = gameTime - activatedTick;
        return compilationEffectEndTick > 0L
                && age >= 0L && age < FOLLOW_TICKS
                && velocity.lengthSqr() == 0.0
                && acceleration.lengthSqr() == 0.0;
    }

    private void captureArrayRelativePosition() {
        if (!(level() instanceof ServerLevel serverLevel) || boundArrayId == null) return;
        ArrayObject array = serverLevel.getData(ModAttachments.ARRAY_MANAGER)
                .getArrayObj(boundArrayId);
        if (array == null) return;
        arrayRelativePosition = ArrayRelativePosition.capture(
                position(), array.rootCircleGlyph().center(),
                array.rootCircleGlyph().surface());
    }

    public void bindGeneratedEntity(com.astune.gyromancy.entity.ball.MagicBallEntity entity,
                                    String scratchKey) {
        if (boundArrayId == null || !(level() instanceof ServerLevel serverLevel)) return;

        entity.bindToArray(boundArrayId);
        serverLevel.getData(ModAttachments.ARRAY_MANAGER)
                .setArrayScratchValue(boundArrayId, scratchKey, ArrayObject.EntityRef.of(entity));
    }

    public double getAverageElementLevel() {
        return averageElementLevel;
    }

    public void setAverageElementLevel(double averageElementLevel) {
        this.averageElementLevel = averageElementLevel;
    }

    public ElementType elementType() {
        return targetElement;
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_TARGET_SIZE.equals(key) || DATA_CURRENT_SIZE.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public @NotNull EntityDimensions getDimensions(@NotNull Pose pose) {
        float size = getFieldSize();
        return EntityDimensions.scalable(size, size);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (tag.contains("Size")) {
            float targetSize = Math.max(SPAWN_SIZE, tag.getFloat("Size"));
            entityData.set(DATA_TARGET_SIZE, targetSize);
            entityData.set(DATA_CURRENT_SIZE, tag.contains("CurrentSize")
                    ? Math.max(SPAWN_SIZE, tag.getFloat("CurrentSize"))
                    : targetSize);
            refreshDimensions();
        } else if (tag.contains("CurrentSize")) {
            setInitialSize(tag.getFloat("CurrentSize"));
        }
        if (tag.hasUUID("ArrayId")) boundArrayId = tag.getUUID("ArrayId");
        if (tag.contains("ArrayRelativeU")) {
            arrayRelativePosition = new ArrayRelativePosition(
                    tag.getDouble("ArrayRelativeU"),
                    tag.getDouble("ArrayRelativeV"),
                    tag.getDouble("ArrayRelativeNormal"));
        }
        if (tag.contains("AccelX")) {
            acceleration = new Vec3(tag.getDouble("AccelX"),
                    tag.getDouble("AccelY"), tag.getDouble("AccelZ"));
        }
        if (tag.contains("InitialSpeed")) {
            initialSpeed = Math.max(0.0, tag.getDouble("InitialSpeed"));
        } else {
            initialSpeed = getDeltaMovement().length();
        }
        velocityThisTick = cappedSpeed(getDeltaMovement());
        setDeltaMovement(velocityThisTick);
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Size", getTargetFieldSize());
        tag.putFloat("CurrentSize", getFieldSize());
        if (boundArrayId != null) tag.putUUID("ArrayId", boundArrayId);
        if (arrayRelativePosition != null) {
            tag.putDouble("ArrayRelativeU", arrayRelativePosition.u());
            tag.putDouble("ArrayRelativeV", arrayRelativePosition.v());
            tag.putDouble("ArrayRelativeNormal", arrayRelativePosition.normal());
        }
        tag.putDouble("AccelX", acceleration.x);
        tag.putDouble("AccelY", acceleration.y);
        tag.putDouble("AccelZ", acceleration.z);
        tag.putDouble("InitialSpeed", initialSpeed);
    }

    public boolean inSphere(Vec3 target, double radius) {
        return MagicBallGeometry.inSphere(position(), target, radius);
    }

    protected List<BlockPos> containedPositions(float size) {
        return MagicBallGeometry.containedPositions(position(), size);
    }

    protected long consumeElement(List<BlockPos> positions, ElementType type, long amount) {
        return ElementStorageManager.INSTANCE.consume(level(), positions, type, amount);
    }

    protected void reduceElementWithMana(ElementType type, long manaCost) {
        reduceElementWithMana(containedPositions(getTargetSize()), type, manaCost);
    }

    private void reduceElementWithMana(List<BlockPos> positions, ElementType type, long manaCost) {
        ElementStorageManager.INSTANCE.reduceWithMana(level(), positions, type, manaCost);
    }
}
