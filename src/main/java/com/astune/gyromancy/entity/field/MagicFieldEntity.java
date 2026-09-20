package com.astune.gyromancy.entity.field;

import com.astune.gyromancy.api.array.ArrayObject;
import com.astune.gyromancy.api.array.MagicArrayManager;
import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.api.field.FieldDirection;
import com.astune.gyromancy.api.field.MagicFieldShape;
import com.astune.gyromancy.api.field.ShapeOrientation;
import com.astune.gyromancy.compile.operator.EntityPayload;
import com.astune.gyromancy.compile.operator.EntityTickContext;
import com.astune.gyromancy.element.ElementStorageManager;
import com.astune.gyromancy.entity.MagicEntity;
import com.astune.gyromancy.registry.ModAttachments;
import com.astune.gyromancy.registry.ModEntityDataSerializers;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A stationary, fixed-shape magic area.
 *
 * <p>Its shape, bounds, total energy, and resulting average energy are fixed
 * when the field is created. The shape owns its own geometry orientation;
 * {@link FieldDirection} remains a separate authored field property.</p>
 */
public abstract class MagicFieldEntity extends MagicEntity {
    private static final String ARRAY_SLOT_PREFIX = "__magic_field/";

    private static final EntityDataAccessor<FieldDirection> DATA_DIRECTION =
            SynchedEntityData.defineId(
                    MagicFieldEntity.class,
                    ModEntityDataSerializers.FIELD_DIRECTION.get()
            );
    private static final EntityDataAccessor<CompoundTag> DATA_SHAPE =
            SynchedEntityData.defineId(MagicFieldEntity.class, EntityDataSerializers.COMPOUND_TAG);

    /**
     * Assigned only during construction or saved-data reconstruction. A live
     * field exposes no replacement API, so its geometry remains immutable.
     */
    private MagicFieldShape shape;
    /** Field-relative broad-phase bounds supplied by the oriented shape. */
    private AABB shapeBounds;
    private final ElementType targetElement;
    private ShapeOrientation shapeOrientation;
    private double energy;
    private double averageEnergy;
    private double averageConcentration;
    private Vec3 anchoredPosition;
    private UUID boundArrayId;

    protected MagicFieldEntity(EntityType<?> type, Level level, MagicFieldShape shape,
                               FieldDirection direction, ElementType targetElement, double energy) {
        super(type, level);
        this.shape = Objects.requireNonNull(shape, "shape");
        this.targetElement = Objects.requireNonNull(targetElement, "targetElement");
        this.energy = validateEnergy(energy);
        this.averageEnergy = averageEnergy(shape, this.energy);
        this.noPhysics = true;
        entityData.set(DATA_DIRECTION, Objects.requireNonNull(direction, "direction"));
        refreshShapeOrientation();
    }

    /** Returns the immutable shape supplied while creating this field. */
    public final MagicFieldShape shape() {
        return shape;
    }

    /** Returns the field's direction, independent of its shape. */
    public FieldDirection direction() {
        return entityData.get(DATA_DIRECTION);
    }

    public void setDirection(FieldDirection direction) {
        entityData.set(DATA_DIRECTION, Objects.requireNonNull(direction, "direction"));
        refreshShapeOrientation();
    }

    /**
     * The orientation copied from and bound to {@link #direction()} for all
     * oriented shape queries. Its forward vector is a distinct immutable
     * {@link Vec3}, so shape math cannot mutate or replace field direction.
     */
    public final ShapeOrientation shapeOrientation() {
        return shapeOrientation;
    }

    /** Total energy supplied while creating this field. */
    public final double energy() {
        return energy;
    }

    /** Average energy calculated from the fixed shape and total energy. */
    public final double averageEnergy() {
        return averageEnergy;
    }

    /** JavaBean aliases for integrations that do not use record-style accessors. */
    public final double getEnergy() {
        return energy();
    }

    public final double getAverageEnergy() {
        return averageEnergy();
    }

    /** The element whose concentration is sampled inside this field. */
    public final ElementType elementType() {
        return targetElement;
    }

    /** The latest average concentration of {@link #elementType()} inside the shape. */
    public final double averageConcentration() {
        return averageConcentration;
    }

    public final double getAverageConcentration() {
        return averageConcentration();
    }

    /** Compatibility alias matching the corresponding MagicBallEntity accessor. */
    public final double getAverageElementLevel() {
        return averageConcentration();
    }

    /** Returns the shape's broad-phase bounds translated to this field's centre position. */
    public final AABB fieldBounds() {
        return shapeBounds.move(position());
    }

    /**
     * Delegates exact membership testing to the supplied shape after only a
     * translation to this field's centre. The shape receives the orientation
     * bound to {@link #direction()}.
     */
    public final boolean isInside(Vec3 point) {
        return shape.isInside(point.subtract(position()), shapeOrientation);
    }

    @Override
    public final List<BlockPos> listInside() {
        List<BlockPos> positions = new ArrayList<>();
        BlockPos.betweenClosedStream(fieldBounds())
                .map(BlockPos::immutable)
                .filter(pos -> shape.isInside(pos.getCenter().subtract(position()), shapeOrientation))
                .forEach(positions::add);
        return positions;
    }

    /** Binds this field to an array, replacing the old field of this exact class. */
    public final void bindToArray(UUID arrayId) {
        boundArrayId = Objects.requireNonNull(arrayId, "arrayId");
        if (level() instanceof ServerLevel serverLevel) {
            MagicArrayManager manager = serverLevel.getData(ModAttachments.ARRAY_MANAGER);
            ArrayObject array = manager.getArrayObj(arrayId);
            if (array != null) {
                replaceExistingArrayField(serverLevel, manager, array);
                setParentIfAbsent(array);
            }
        }
        bindPayloadToArray(arrayId);
    }

    /** The owning array, if this field was emitted by one. */
    public final UUID boundArrayId() {
        return boundArrayId;
    }

    /** Once the first tick fixes its anchor, direct teleports are ignored too. */
    @Override
    public final void setPos(double x, double y, double z) {
        if (anchoredPosition == null) {
            super.setPos(x, y, z);
        }
    }

    /** Binds a ball emitted by this field's payload to the same array. */
    public final void bindGeneratedEntity(com.astune.gyromancy.entity.ball.MagicBallEntity entity,
                                          String scratchKey) {
        if (boundArrayId == null || !(level() instanceof ServerLevel serverLevel)) return;

        entity.bindToArray(boundArrayId);
        serverLevel.getData(ModAttachments.ARRAY_MANAGER)
                .setArrayScratchValue(boundArrayId, scratchKey, ArrayObject.EntityRef.of(entity));
    }

    @Override
    protected final boolean tickBeforePayload() {
        if (anchoredPosition == null) {
            anchoredPosition = position();
        } else if (!anchoredPosition.equals(position())) {
            super.setPos(anchoredPosition.x, anchoredPosition.y, anchoredPosition.z);
        }
        velocityThisTick = Vec3.ZERO;
        setDeltaMovement(Vec3.ZERO);
        if (!level().isClientSide) refreshAverageConcentration();
        return true;
    }

    @Override
    protected final void tickAfterPayload() {
        // Payloads may not turn a field into a moving entity.
        velocityThisTick = Vec3.ZERO;
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    protected final EntityTickContext payloadContext(Map<String, Object> runtimeData) {
        return EntityTickContext.from(this, runtimeData);
    }

    @Override
    protected List<? extends EntityPayload> defaultPayload() {
        return List.of();
    }

    @Override
    public @NotNull EntityDimensions getDimensions(@NotNull Pose pose) {
        float width = (float) Math.max(0.01,
                Math.max(shapeBounds.getXsize(), shapeBounds.getZsize()));
        float height = (float) Math.max(0.01, shapeBounds.getYsize());
        return EntityDimensions.scalable(width, height);
    }

    /**
     * A field is centred on its position, whereas normal entity dimensions
     * start at its feet. Returning the shape broad-phase box directly keeps
     * the real entity AABB exactly aligned with {@link #fieldBounds()}.
     */
    @Override
    protected AABB makeBoundingBox() {
        return shapeBounds == null ? super.makeBoundingBox() : fieldBounds();
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Shape", Tag.TAG_COMPOUND)) {
            restoreShape(readShape(tag.getCompound("Shape")));
        }
        if (tag.contains("Direction")) {
            FieldDirection.CODEC
                    .parse(NbtOps.INSTANCE, tag.get("Direction"))
                    .result()
                    .ifPresent(this::setDirection);
        }
        if (tag.contains("Energy")) {
            energy = validateEnergy(tag.getDouble("Energy"));
        }
        averageEnergy = averageEnergy(shape, energy);
        syncShapeData();
        if (tag.hasUUID("ArrayId")) {
            boundArrayId = tag.getUUID("ArrayId");
            setArrayParentIfAbsent(boundArrayId);
            bindPayloadToArray(boundArrayId);
        }
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("Energy", energy);
        CompoundTag shapeTag = new CompoundTag();
        addShapeData(shapeTag);
        if (!shapeTag.isEmpty()) tag.put("Shape", shapeTag);
        FieldDirection.CODEC
                .encodeStart(NbtOps.INSTANCE, direction())
                .result()
                .ifPresent(directionTag -> tag.put("Direction", directionTag));
        if (boundArrayId != null) tag.putUUID("ArrayId", boundArrayId);
    }

    /**
     * Writes this field's concrete immutable shape. A field implementation
     * with a custom external shape overrides this alongside {@link
     * #readShape(CompoundTag)}.
     */
    protected void addShapeData(@NotNull CompoundTag tag) {
    }

    /**
     * Recreates this field's concrete shape during load or client sync. The
     * default preserves the construction shape for non-serialised shapes.
     */
    protected MagicFieldShape readShape(@NotNull CompoundTag tag) {
        return shape;
    }

    private void replaceExistingArrayField(ServerLevel level, MagicArrayManager manager, ArrayObject array) {
        String slot = ARRAY_SLOT_PREFIX + getClass().getName();
        Object previous = array.scratchData().get(slot);
        if (previous instanceof ArrayObject.EntityRef ref
                && ref.resolve(level) instanceof MagicFieldEntity oldField
                && oldField != this && oldField.isAlive()) {
            oldField.discard();
        }
        manager.setArrayScratchValue(array.arrayId(), slot, ArrayObject.EntityRef.of(this));
    }

    /**
     * Samples block-centre concentrations from the shape bounds and retains
     * only points accepted by the externally supplied shape. This is
     * intentionally not an AABB-only calculation.
     */
    private void refreshAverageConcentration() {
        List<BlockPos> positions = listInside();
        averageConcentration = positions.isEmpty()
                ? 0.0
                : (double) ElementStorageManager.INSTANCE.sum(level(), positions, targetElement)
                        / positions.size();
    }

    private static AABB validateBounds(AABB bounds) {
        Objects.requireNonNull(bounds, "shape bounds");
        if (!Double.isFinite(bounds.minX) || !Double.isFinite(bounds.minY) || !Double.isFinite(bounds.minZ)
                || !Double.isFinite(bounds.maxX) || !Double.isFinite(bounds.maxY)
                || !Double.isFinite(bounds.maxZ)
                || bounds.getXsize() <= 0.0 || bounds.getYsize() <= 0.0 || bounds.getZsize() <= 0.0) {
            throw new IllegalArgumentException("Magic field shape bounds must be finite and non-empty");
        }
        return bounds;
    }

    private void refreshShapeOrientation() {
        shapeOrientation = new ShapeOrientation(direction());
        shapeBounds = validateBounds(shape.bounds(shapeOrientation));
        // Entity only recalculates its AABB when explicitly refreshed. This
        // also applies synced direction changes on the client.  Minecraft's
        // refreshDimensions() path uses EntityDimensions and therefore cannot
        // preserve a non-square, rotated broad-phase box; restore the exact
        // shape bounds immediately afterwards.
        refreshDimensions();
        setBoundingBox(fieldBounds());
    }

    /** Applies a serialised shape without making live field geometry mutable. */
    private void restoreShape(MagicFieldShape restoredShape) {
        shape = Objects.requireNonNull(restoredShape, "restoredShape");
        refreshShapeOrientation();
    }

    /** Synchronises the concrete shape after the field subclass has initialised it. */
    protected final void syncShapeData() {
        if (level().isClientSide) return;
        CompoundTag tag = new CompoundTag();
        addShapeData(tag);
        entityData.set(DATA_SHAPE, tag);
    }

    private static double validateEnergy(double energy) {
        if (!Double.isFinite(energy) || energy < 0.0) {
            throw new IllegalArgumentException("Magic field energy must be finite and non-negative");
        }
        return energy;
    }

    private static double averageEnergy(MagicFieldShape shape, double energy) {
        double averageEnergy = shape.averageEnergy(energy);
        if (!Double.isFinite(averageEnergy)) {
            throw new IllegalArgumentException("Magic field average energy must be finite");
        }
        return averageEnergy;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DIRECTION, WindFieldEntity.DEFAULT_DIRECTION);
        builder.define(DATA_SHAPE, new CompoundTag());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);

        if (DATA_DIRECTION.equals(key)) {
            refreshShapeOrientation();
        } else if (DATA_SHAPE.equals(key)) {
            CompoundTag shapeTag = entityData.get(DATA_SHAPE);
            if (!shapeTag.isEmpty()) restoreShape(readShape(shapeTag));
        }
    }
}
