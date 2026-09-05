package com.astune.gyromancy.entity.ball;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.compile.operator.EntityPayload;
import com.astune.gyromancy.compile.operator.EntityTickContext;
import com.astune.gyromancy.compile.operator.ElementVolumeOp;
import com.astune.gyromancy.element.ElementStorageManager;
import com.astune.gyromancy.entity.MagicEntity;
import com.astune.gyromancy.entity.ArrayRelativePosition;
import com.astune.gyromancy.api.geometry.SurfaceFrame;
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
    public static final int DEFAULT_READY_TO_DISCARD_MAX_LIFETIME = 200;
    public static final double MIN_LAUNCH_SIZE = 0.5f;

    private UUID boundArrayId;
    private ArrayRelativePosition arrayRelativePosition;
    private Vec3 parentRelativePosition;
    private SurfaceFrame creationArraySurface;
    private final ElementType targetElement;
    private double averageElementLevel;
    private boolean impactThisTick;
    private boolean blockImpactThisTick;
    Vec3 acceleration = Vec3.ZERO;
    private Vec3 pendingVelocity = Vec3.ZERO;
    private Vec3 pendingAcceleration = Vec3.ZERO;

    private boolean launched = false;
    private int lifetime;

    private int maxLifetime = DEFAULT_READY_TO_DISCARD_MAX_LIFETIME;

    public MagicBallEntity(EntityType<? extends MagicBallEntity> type, Level level, ElementType targetElement) {
        super(type, level);
        this.targetElement = targetElement;
        this.noPhysics = true;
        this.maxLifetime = (int) (getTargetBallSize() * DEFAULT_READY_TO_DISCARD_MAX_LIFETIME);
    }

    public MagicBallEntity(EntityType<? extends MagicBallEntity> type, Level level, ElementType targetElement, Vec3 velocity, Vec3 acceleration) {
        super(type, level);
        this.pendingVelocity = velocity;
        this.pendingAcceleration = acceleration;
        this.targetElement = targetElement;
        this.noPhysics = true;
        this.maxLifetime = (int) (getTargetBallSize() * DEFAULT_READY_TO_DISCARD_MAX_LIFETIME);
    }

    @Override
    protected boolean tickBeforePayload() {
        if (advanceDiscardTimer()) return false;

        impactThisTick = false;
        blockImpactThisTick = false;
        updateArrayRelativePosition();
        growIntoTargetSize();

        if (!launched && getTargetBallSize() < MIN_LAUNCH_SIZE){
            launchIfReady();
        }

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
        blockImpactThisTick = blockHit.getType() != HitResult.Type.MISS;
        impactThisTick = blockImpactThisTick || hitLivingEntity(velocityThisTick);

        addDeltaMovement(acceleration);

        return true;
    }

    /** Starts this projectile's deferred cleanup timer. */
    public final void readyToDiscard() {
        lifetime = 1;
    }

    public final int lifetime() {
        return lifetime;
    }

    public final int maxLifetime() {
        return maxLifetime;
    }

    public int getMaxLifetime() {
        return maxLifetime;
    }
    public final void setMaxLifetime(int maxLifetime) {
        this.maxLifetime = Math.max(0, maxLifetime);
    }

    private boolean advanceDiscardTimer() {
        if (level().isClientSide || lifetime <= 0) return false;
        if (++lifetime <= maxLifetime) return false;
        discard();
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

    public boolean hasBlockImpactThisTick() {
        return blockImpactThisTick;
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

        rotatePendingMotionToCurrentArraySurface();
        launched = true;

        acceleration = pendingAcceleration;
        setDeltaMovement(pendingVelocity);
    }

    public void bindToArray(UUID arrayId) {
        this.boundArrayId = arrayId;
        if (level() instanceof ServerLevel serverLevel) {
            ArrayObject array = serverLevel.getData(ModAttachments.ARRAY_MANAGER)
                    .getArrayObj(arrayId);
            if (array != null) setParentIfAbsent(array);
        }
        captureArrayRelativePosition();
        bindPayloadToArray(arrayId);
    }

    public SurfaceFrame currentArrayFrame() {
        if (!(level() instanceof ServerLevel serverLevel) || boundArrayId == null) return null;
        ArrayObject array = serverLevel.getData(ModAttachments.ARRAY_MANAGER).getArrayObj(boundArrayId);
        return array == null ? null : array.rootCircleGlyph().surface();
    }

    private void updateArrayRelativePosition() {
        if (parent() instanceof Entity parentEntity) {
            updateEntityRelativePosition(parentEntity);
            return;
        }
        if (!(parent() instanceof ArrayObject)
                || !(level() instanceof ServerLevel serverLevel)
                || boundArrayId == null) {
            arrayRelativePosition = null;
            return;
        }
        ArrayObject array = serverLevel.getData(ModAttachments.ARRAY_MANAGER)
                .getArrayObj(boundArrayId);
        if (array == null) {
            arrayRelativePosition = null;
            return;
        }
        if (launched) {
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

    private void updateEntityRelativePosition(Entity parentEntity) {
        if (launched || !parentEntity.isAlive() || parentEntity.level() != level()) {
            parentRelativePosition = null;
            return;
        }
        if (parentRelativePosition == null) {
            parentRelativePosition = position().subtract(parentEntity.position());
        }
        setPos(parentEntity.position().add(parentRelativePosition));
    }

    private void captureArrayRelativePosition() {
        if (!(level() instanceof ServerLevel serverLevel) || boundArrayId == null) return;
        ArrayObject array = serverLevel.getData(ModAttachments.ARRAY_MANAGER)
                .getArrayObj(boundArrayId);
        if (array == null) return;
        if (creationArraySurface == null) {
            creationArraySurface = array.rootCircleGlyph().surface();
        }
        arrayRelativePosition = ArrayRelativePosition.capture(
                position(), array.rootCircleGlyph().center(),
                array.rootCircleGlyph().surface());
    }

    /**
     * Reorients motion authored against the array's creation frame to the
     * frame that exists when this ball is actually launched.
     */
    private void rotatePendingMotionToCurrentArraySurface() {
        if (!(level() instanceof ServerLevel serverLevel)
                || boundArrayId == null || creationArraySurface == null) return;

        ArrayObject array = serverLevel.getData(ModAttachments.ARRAY_MANAGER)
                .getArrayObj(boundArrayId);
        if (array == null) return;

        SurfaceFrame currentSurface = array.rootCircleGlyph().surface();
        pendingVelocity = rotateBetweenArraySurfaces(
                pendingVelocity, creationArraySurface, currentSurface);
        //pendingAcceleration = rotateBetweenArraySurfaces(
        //        pendingAcceleration, creationArraySurface, currentSurface);
    }

    /**
     * Calculates the angle between two orthonormal array frames and applies
     * that frame rotation to a world-space vector using Rodrigues' formula.
     */
    private static Vec3 rotateBetweenArraySurfaces(
            Vec3 vector, SurfaceFrame from, SurfaceFrame to) {
        double trace = from.axisU().dot(to.axisU())
                + from.axisV().dot(to.axisV())
                + from.normal().dot(to.normal());
        double cosine = Math.max(-1.0, Math.min(1.0, (trace - 1.0) * 0.5));
        double angle = Math.acos(cosine);
        if (angle < 1.0E-10 || vector.lengthSqr() < 1.0E-12) return vector;

        Vec3 axis = from.axisU().cross(to.axisU())
                .add(from.axisV().cross(to.axisV()))
                .add(from.normal().cross(to.normal()));
        if (axis.lengthSqr() < 1.0E-12) {
            axis = halfTurnAxis(from.axisU(), to.axisU());
            if (axis.lengthSqr() < 1.0E-12) {
                axis = halfTurnAxis(from.axisV(), to.axisV());
            }
            if (axis.lengthSqr() < 1.0E-12) {
                axis = halfTurnAxis(from.normal(), to.normal());
            }
        }
        if (axis.lengthSqr() < 1.0E-12) return vector;

        axis = axis.normalize();
        double sine = Math.sin(angle);
        double cosineAngle = Math.cos(angle);
        return vector.scale(cosineAngle)
                .add(axis.cross(vector).scale(sine))
                .add(axis.scale(axis.dot(vector) * (1.0 - cosineAngle)));
    }

    private static Vec3 halfTurnAxis(Vec3 from, Vec3 to) {
        Vec3 axis = from.add(to);
        return axis.lengthSqr() < 1.0E-12 ? Vec3.ZERO : axis.normalize();
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
        if (tag.contains("ArrayCreationAxisUX")) {
            Vec3 axisU = new Vec3(
                    tag.getDouble("ArrayCreationAxisUX"),
                    tag.getDouble("ArrayCreationAxisUY"),
                    tag.getDouble("ArrayCreationAxisUZ"));
            Vec3 axisV = new Vec3(
                    tag.getDouble("ArrayCreationAxisVX"),
                    tag.getDouble("ArrayCreationAxisVY"),
                    tag.getDouble("ArrayCreationAxisVZ"));
            Vec3 normal = new Vec3(
                    tag.getDouble("ArrayCreationNormalX"),
                    tag.getDouble("ArrayCreationNormalY"),
                    tag.getDouble("ArrayCreationNormalZ"));
            creationArraySurface = new SurfaceFrame(Vec3.ZERO, axisU, axisV, normal);
        }
        if (tag.contains("PendingVelX")) {
            pendingVelocity = new Vec3(tag.getDouble("PendingVelX"), tag.getDouble("PendingVelY"), tag.getDouble("PendingVelZ"));
        }
        if (tag.contains("PendingAccelX")) {
            pendingAcceleration = new Vec3(tag.getDouble("PendingAccelX"), tag.getDouble("PendingAccelY"), tag.getDouble("PendingAccelZ"));
        }
        launched = tag.getBoolean("Launched");
        lifetime = Math.max(0, tag.getInt("DiscardLifetime"));
        if (tag.contains("MaxDiscardLifetime")) {
            maxLifetime = Math.max(0, tag.getInt("MaxDiscardLifetime"));
        }
        if (boundArrayId != null) {
            setArrayParentIfAbsent(boundArrayId);
            bindPayloadToArray(boundArrayId);
        }

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
        if (creationArraySurface != null) {
            tag.putDouble("ArrayCreationAxisUX", creationArraySurface.axisU().x);
            tag.putDouble("ArrayCreationAxisUY", creationArraySurface.axisU().y);
            tag.putDouble("ArrayCreationAxisUZ", creationArraySurface.axisU().z);
            tag.putDouble("ArrayCreationAxisVX", creationArraySurface.axisV().x);
            tag.putDouble("ArrayCreationAxisVY", creationArraySurface.axisV().y);
            tag.putDouble("ArrayCreationAxisVZ", creationArraySurface.axisV().z);
            tag.putDouble("ArrayCreationNormalX", creationArraySurface.normal().x);
            tag.putDouble("ArrayCreationNormalY", creationArraySurface.normal().y);
            tag.putDouble("ArrayCreationNormalZ", creationArraySurface.normal().z);
        }
        tag.putDouble("PendingVelX", pendingVelocity.x);
        tag.putDouble("PendingVelY", pendingVelocity.y);
        tag.putDouble("PendingVelZ", pendingVelocity.z);
        tag.putDouble("PendingAccelX", pendingAcceleration.x);
        tag.putDouble("PendingAccelY", pendingAcceleration.y);
        tag.putDouble("PendingAccelZ", pendingAcceleration.z);
        tag.putBoolean("Launched", launched);
        tag.putInt("DiscardLifetime", lifetime);
        tag.putInt("MaxDiscardLifetime", maxLifetime);

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
