package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.api.effect.MagicEffect;
import com.astune.gyromancy.element.ElementStorageManager;
import com.astune.gyromancy.entity.field.MagicFieldEntity;
import com.astune.gyromancy.entity.ball.MagicBallEntity;
import com.astune.gyromancy.api.geometry.SurfaceFrame;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

public final class EntityTickContext {
    private final MagicEffect owner;
    private final Object parent;
    private final Level level;
    private final int tickCount;
    private final Vec3 position;
    private final Vec3 velocity;
    private final Vec3 facing;
    private final Vec3 acceleration;
    private final SurfaceFrame arrayFrame;
    private final AABB bounds;
    private final boolean clientSide;
    private final boolean alive;
    private final boolean fullyGrown;
    private final boolean impact;
    private final boolean blockImpact;
    private final float size;
    private final float targetSize;
    private double averageElementLevel;
    private final Map<String, Object> data;
    private final Runnable discard;
    private final Consumer<Entity> addFreshEntity;
    private final BiConsumer<MagicBallEntity, String> bindGeneratedEntity;
    private final DoubleConsumer setTargetVolume;
    private final DoubleConsumer setAverageElementLevel;
    public EntityTickContext(MagicEffect owner, Level level, int tickCount, Vec3 position, Vec3 velocity,
                             Vec3 facing, Vec3 acceleration, AABB bounds, boolean clientSide, boolean alive,
                             boolean fullyGrown, boolean impact, float size, float targetSize,
                             double averageElementLevel, Map<String, Object> data,
                             Runnable discard, Consumer<Entity> addFreshEntity,
                             BiConsumer<MagicBallEntity, String> bindGeneratedEntity,
                             DoubleConsumer setTargetVolume, DoubleConsumer setAverageElementLevel) {
        this(owner, level, tickCount, position, velocity, facing, acceleration, bounds, clientSide, alive,
                fullyGrown, impact, size, targetSize, averageElementLevel, data, discard, addFreshEntity,
                bindGeneratedEntity, setTargetVolume, setAverageElementLevel, null);
    }

    public EntityTickContext(MagicEffect owner, Level level, int tickCount, Vec3 position, Vec3 velocity,
                             Vec3 facing, Vec3 acceleration, AABB bounds, boolean clientSide, boolean alive,
                             boolean fullyGrown, boolean impact, float size, float targetSize,
                             double averageElementLevel, Map<String, Object> data,
                             Runnable discard, Consumer<Entity> addFreshEntity,
                             BiConsumer<MagicBallEntity, String> bindGeneratedEntity,
                             DoubleConsumer setTargetVolume, DoubleConsumer setAverageElementLevel,
                             SurfaceFrame arrayFrame) {
        this(owner, level, tickCount, position, velocity, facing, acceleration, bounds, clientSide, alive,
                fullyGrown, impact, size, targetSize, averageElementLevel, data, discard, addFreshEntity,
                bindGeneratedEntity, setTargetVolume, setAverageElementLevel, arrayFrame, null);
    }

    public EntityTickContext(MagicEffect owner, Level level, int tickCount, Vec3 position, Vec3 velocity,
                             Vec3 facing, Vec3 acceleration, AABB bounds, boolean clientSide, boolean alive,
                             boolean fullyGrown, boolean impact, float size, float targetSize,
                             double averageElementLevel, Map<String, Object> data,
                             Runnable discard, Consumer<Entity> addFreshEntity,
                             BiConsumer<MagicBallEntity, String> bindGeneratedEntity,
                             DoubleConsumer setTargetVolume, DoubleConsumer setAverageElementLevel,
                             SurfaceFrame arrayFrame, Object parent) {
        this(owner, level, tickCount, position, velocity, facing, acceleration, bounds, clientSide, alive,
                fullyGrown, impact, size, targetSize, averageElementLevel, data, discard, addFreshEntity,
                bindGeneratedEntity, setTargetVolume, setAverageElementLevel, arrayFrame, parent, false);
    }

    public EntityTickContext(MagicEffect owner, Level level, int tickCount, Vec3 position, Vec3 velocity,
                             Vec3 facing, Vec3 acceleration, AABB bounds, boolean clientSide, boolean alive,
                             boolean fullyGrown, boolean impact, float size, float targetSize,
                             double averageElementLevel, Map<String, Object> data,
                             Runnable discard, Consumer<Entity> addFreshEntity,
                             BiConsumer<MagicBallEntity, String> bindGeneratedEntity,
                             DoubleConsumer setTargetVolume, DoubleConsumer setAverageElementLevel,
                             SurfaceFrame arrayFrame, Object parent, boolean blockImpact) {
        this.owner = owner;
        this.parent = parent;
        this.level = level;
        this.tickCount = tickCount;
        this.position = position;
        this.velocity = velocity;
        this.facing = facing;
        this.acceleration = acceleration;
        this.arrayFrame = arrayFrame;
        this.bounds = bounds;
        this.clientSide = clientSide;
        this.alive = alive;
        this.fullyGrown = fullyGrown;
        this.impact = impact;
        this.blockImpact = blockImpact;
        this.size = size;
        this.targetSize = targetSize;
        this.averageElementLevel = averageElementLevel;
        this.data = data;
        this.discard = discard;
        this.addFreshEntity = addFreshEntity;
        this.bindGeneratedEntity = bindGeneratedEntity;
        this.setTargetVolume = setTargetVolume;
        this.setAverageElementLevel = setAverageElementLevel;
    }

    public static EntityTickContext from(MagicBallEntity entity, Map<String, Object> data, Vec3 acceleration) {
        return new EntityTickContext(entity, entity.level(), entity.tickCount, entity.position(),
                entity.velocityThisTick(), entity.getLookAngle(), acceleration, entity.getBoundingBox(),
                entity.level().isClientSide,
                entity.isAlive(), entity.isFullyGrown(), entity.hasImpactThisTick(), entity.getBallSize(),
                entity.getTargetSize(), entity.getAverageElementLevel(), data,
                entity::discard, entity.level()::addFreshEntity, entity::bindGeneratedEntity,
                entity::setTargetVolume, entity::setAverageElementLevel, entity.currentArrayFrame(),
                entity.parent(), entity.hasBlockImpactThisTick());
    }

    /** Builds the single-tick context used by non-entity one-shot effect hosts. */
    public static EntityTickContext forInstant(MagicEffect effect, Map<String, Object> data,
                                               Vec3 position, AABB bounds, Vec3 facing,
                                               float size, double averageElementLevel) {
        Level level = effect.level();
        return new EntityTickContext(effect, level, 0, position, Vec3.ZERO, facing, Vec3.ZERO,
                bounds, false, effect.isAlive(), true, false, size, size, averageElementLevel, data,
                effect::discard,
                level == null ? entity -> {} : level::addFreshEntity,
                (entity, key) -> {}, ignored -> {}, ignored -> {}, null, null, false);
    }

    public static EntityTickContext from(MagicFieldEntity entity, Map<String, Object> data) {
        float size = (float) Math.max(entity.fieldBounds().getXsize(),
                Math.max(entity.fieldBounds().getYsize(), entity.fieldBounds().getZsize()));
        return new EntityTickContext(entity, entity.level(), entity.tickCount, entity.position(),
                Vec3.ZERO, entity.direction().vector(), Vec3.ZERO, entity.fieldBounds(),
                entity.level().isClientSide, entity.isAlive(), true, false, size, size,
                entity.averageConcentration(), data, entity::discard, entity.level()::addFreshEntity,
                entity::bindGeneratedEntity, ignored -> {}, ignored -> {}, null, entity.parent());
    }

    public MagicEffect owner() { return owner; }

    public Object parent() { return parent; }

    public Level level() { return level; }

    public int tickCount() { return tickCount; }

    public Vec3 position() { return position; }

    public Vec3 velocity() { return velocity; }

    public Vec3 facing() { return facing; }

    public Vec3 acceleration() { return acceleration; }

    public SurfaceFrame arrayFrame() { return arrayFrame; }

    public AABB bounds() { return bounds; }

    public boolean isClientSide() { return clientSide; }

    public boolean isAlive() { return alive; }

    public boolean isFullyGrown() { return fullyGrown; }

    public boolean hasImpact() { return impact; }

    /** Whether the owner hit a block during the movement step that preceded this payload tick. */
    public boolean hasBlockImpact() { return blockImpact; }

    public float size() { return size; }

    public float targetSize() { return targetSize; }

    public double averageElementLevel() { return averageElementLevel; }

    public ElementStorageManager elementStorage() { return ElementStorageManager.INSTANCE; }

    public void setTargetVolume(double volume) {
        setTargetVolume.accept(volume);
    }

    public void setAverageElementLevel(double averageElementLevel) {
        this.averageElementLevel = averageElementLevel;
        setAverageElementLevel.accept(averageElementLevel);
    }

    public void discard() { discard.run(); }

    public void addFreshEntity(Entity spawned) { addFreshEntity.accept(spawned); }

    public void bindGeneratedEntity(MagicBallEntity spawned, String scratchKey) {
        bindGeneratedEntity.accept(spawned, scratchKey);
    }

    public void put(String key, Object value) { data.put(key, value); }

    public long longValue(String key) {
        Object value = data.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    public int intValue(String key, int fallback) {
        Object value = data.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    public float floatValue(String key, float fallback) {
        Object value = data.get(key);
        return value instanceof Number number ? number.floatValue() : fallback;
    }

    public double doubleValue(String key, double fallback) {
        Object value = data.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    public boolean booleanValue(String key) {
        Object value = data.get(key);
        return value instanceof Boolean bool && bool;
    }
}
