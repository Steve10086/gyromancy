package com.astune.gyromancy.entity.ball;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.compile.operator.EntityPayload;
import com.astune.gyromancy.compile.operator.EntityTickContext;
import com.astune.gyromancy.compile.operator.ElementVolumeOp;
import com.astune.gyromancy.element.ElementStorageManager;
import com.astune.gyromancy.entity.MagicEntity;
import com.astune.gyromancy.entity.ArrayRelativePosition;
import com.astune.gyromancy.registry.ModAttachments;
import com.astune.gyromancy.util.MagicBallGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class MagicBallEntity extends MagicEntity {
    private static final EntityDataAccessor<Float> DATA_TARGET_SIZE =
            SynchedEntityData.defineId(MagicBallEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CURRENT_SIZE =
            SynchedEntityData.defineId(MagicBallEntity.class, EntityDataSerializers.FLOAT);
    protected static final float SPAWN_SIZE = 0.1F;
    private static final int GROWTH_RATE = 2;
    private UUID boundArrayId;
    private ArrayRelativePosition arrayRelativePosition;
    private final ElementType targetElement;
    private double averageElementLevel;
    private boolean impactThisTick;
    Vec3 acceleration = Vec3.ZERO;
    private Vec3 pendingVelocity = Vec3.ZERO;
    private Vec3 pendingAcceleration = Vec3.ZERO;

    private boolean launched = false;

    public MagicBallEntity(EntityType<? extends MagicBallEntity> type, Level level, ElementType targetElement) {
        super(type, level);
        this.targetElement = targetElement;
        this.noPhysics = true;
    }

    public MagicBallEntity(EntityType<? extends MagicBallEntity> type, Level level, ElementType targetElement, Vec3 velocity, Vec3 acceleration) {
        super(type, level);
        this.pendingVelocity = velocity;
        this.pendingAcceleration = acceleration;
        this.targetElement = targetElement;
        this.noPhysics = true;
    }

    @Override
    protected boolean tickBeforePayload() {
        updateArrayRelativePosition();
        growIntoTargetSize();

        if (!launched && !isFullyGrown()) {
            setDeltaMovement(Vec3.ZERO);
            return false;
        }
        launchIfReady();

        Vec3 start = position();
        Vec3 end = start.add(velocityThisTick);
        HitResult blockHit = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation();
        }

        setPos(end);
        impactThisTick = blockHit.getType() != HitResult.Type.MISS || hitLivingEntity(velocityThisTick);

        addDeltaMovement(acceleration);

        return true;
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
        var searchBox = getBoundingBox().expandTowards(velocity).inflate(0.1);
        return !level().getEntitiesOfClass(LivingEntity.class, searchBox,
                LivingEntity::isAlive).isEmpty();
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
    public boolean isLaunched() {
        return launched;
    }
    public int getGrowthTicks() {
        return getTargetBallSize() <= SPAWN_SIZE ? 0 : (int)Math.ceil(100.0F / GROWTH_RATE);
    }

    public void setBallSize(float size) {
        setTargetSize(size);
    }

    public void setTargetVolume(double volume) {
        Vec3 velocity = getDeltaMovement();
        setTargetSize((float)MagicBallGeometry.sizeForVolume(volume, SPAWN_SIZE));
        setDeltaMovement(velocity);
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

    private void launchIfReady() {
        if (launched) return;
        launched = true;
        acceleration = pendingAcceleration;
        setDeltaMovement(pendingVelocity);
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
        // Pending launch values intentionally do not participate in this policy.
        // MagicBallEntity alone decides whether it follows; the array only
        // supplies its persisted creation window and current geometry.
        if (!MagicBallFollowPolicy.shouldFollow(serverLevel.getGameTime(),
                array.compilationEffectEndTick(), getDeltaMovement(), payloadAcceleration())) {
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

    private void captureArrayRelativePosition() {
        if (!(level() instanceof ServerLevel serverLevel) || boundArrayId == null) return;
        ArrayObject array = serverLevel.getData(ModAttachments.ARRAY_MANAGER)
                .getArrayObj(boundArrayId);
        if (array == null) return;
        arrayRelativePosition = ArrayRelativePosition.capture(
                position(), array.rootCircleGlyph().center(),
                array.rootCircleGlyph().surface());
    }

    public void bindGeneratedEntity(MagicBallEntity entity, String scratchKey) {
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

    protected Vec3 launchVelocity(Vec3 velocity, double arrowSizeSum, double liftDirection) {
        double speed = velocity.length();
        double lift = ((arrowSizeSum - speed) + 0.2 * speed) * liftDirection;
        return velocity.add(0.0, lift, 0.0);
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
        super.readAdditionalSaveData(tag);
        if (tag.contains("Size")) setBallSize(tag.getFloat("Size"));
        if (tag.contains("CurrentSize")) entityData.set(DATA_CURRENT_SIZE, tag.getFloat("CurrentSize"));
        if (tag.hasUUID("ArrayId")) boundArrayId = tag.getUUID("ArrayId");
        if (tag.contains("ArrayRelativeU")) {
            arrayRelativePosition = new ArrayRelativePosition(
                    tag.getDouble("ArrayRelativeU"),
                    tag.getDouble("ArrayRelativeV"),
                    tag.getDouble("ArrayRelativeNormal"));
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
        tag.putFloat("Size", getTargetBallSize());
        tag.putFloat("CurrentSize", getBallSize());
        if (boundArrayId != null) tag.putUUID("ArrayId", boundArrayId);
        if (arrayRelativePosition != null) {
            tag.putDouble("ArrayRelativeU", arrayRelativePosition.u());
            tag.putDouble("ArrayRelativeV", arrayRelativePosition.v());
            tag.putDouble("ArrayRelativeNormal", arrayRelativePosition.normal());
        }
        tag.putDouble("PendingVelX", pendingVelocity.x);
        tag.putDouble("PendingVelY", pendingVelocity.y);
        tag.putDouble("PendingVelZ", pendingVelocity.z);
        tag.putDouble("PendingAccelX", pendingAcceleration.x);
        tag.putDouble("PendingAccelY", pendingAcceleration.y);
        tag.putDouble("PendingAccelZ", pendingAcceleration.z);
        tag.putBoolean("Launched", launched);

    }

    public boolean inSphere(Vec3 target, double radius) {
        return MagicBallGeometry.inSphere(position(), target, radius);
    }

    protected List<BlockPos> containedPositions(float size) {
        return MagicBallGeometry.containedPositions(position(), size);
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
        return ElementStorageManager.INSTANCE.consume(level(), positions, type, amount);
    }

    protected void reduceElementWithMana(ElementType type, long manaCost) {
        reduceElementWithMana(containedPositions(getTargetSize()), type, manaCost);
    }

    private void reduceElementWithMana(List<BlockPos> positions, ElementType type, long manaCost) {
        ElementStorageManager.INSTANCE.reduceWithMana(
                level(), positions, type, manaCost);
    }

}
