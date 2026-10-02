package com.astune.gyromancy.api.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The host contract shared by entity-backed magic effects and non-entity
 * effect hosts.
 *
 * <p>An entity-backed host inherits {@code level}, {@code position},
 * {@code isAlive}, {@code discard}, the movement and rotation members, and
 * {@code getRandom} directly from {@link Entity}. It supplies {@link #bounds()},
 * {@link #gravity()}, {@link #listInside()} and {@link #parentPending()} from
 * its payload state, while {@link #entity()} resolves to the host itself. A
 * non-entity host supplies fixed values and no-op defaults. Payloads read host
 * state only through this interface, so the same payload protocol can run on
 * either kind of host.</p>
 */
public interface MagicEffect {
    Level level();

    Vec3 position();

    AABB bounds();

    boolean isAlive();

    void discard();

    /** The host as an entity when one exists; {@code null} for non-entity hosts. */
    default @Nullable Entity entity() {
        return this instanceof Entity e ? e : null;
    }

    default Vec3 getDeltaMovement() {
        return Vec3.ZERO;
    }

    default void addDeltaMovement(Vec3 movement) {
    }

    default void setPos(double x, double y, double z) {
    }

    default void setPos(Vec3 position) {
        setPos(position.x, position.y, position.z);
    }

    default void setYRot(float yRot) {
    }

    default void setXRot(float xRot) {
    }

    default RandomSource getRandom() {
        return level().getRandom();
    }

    default double gravity() {
        return 0.0;
    }

    List<BlockPos> listInside();

    default boolean parentPending() {
        return false;
    }
}
