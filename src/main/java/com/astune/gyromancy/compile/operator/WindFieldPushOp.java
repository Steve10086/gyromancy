package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.entity.field.WindFieldEntity;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Applies the wind field's fixed direction to every entity whose centre is
 * inside the field shape. The correction is repeated every server tick and is
 * proportional to the field's immutable average energy.
 */
public final class WindFieldPushOp extends OnEntityTickOp {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            Gyromancy.MODID, "wind_field_push");
    public static final Codec<WindFieldPushOp> CODEC = Codec.unit(WindFieldPushOp::new);

    /** Matches MomentumOp's per-tick scale so energy maps to a gentle impulse. */
    public static final double FORCE_SCALE = 1.0 / 10.0;
    private static final double DIRECTION_EPSILON = 1.0E-8;

    public WindFieldPushOp() {
    }

    @Override
    public ResourceLocation typeId() {
        return ID;
    }

    @Override
    protected Codec<WindFieldPushOp> codec() {
        return CODEC;
    }

    @Override
    public void onEntityTick(EntityTickContext ctx) {
        if (ctx.isClientSide() || !(ctx.owner() instanceof WindFieldEntity field)) return;

        Vec3 direction = field.direction().vector();
        Vec3 push = pushFor(direction, field.averageEnergy());
        if (push.lengthSqr() < DIRECTION_EPSILON) return;
        for (Entity target : ctx.level().getEntities(field, field.fieldBounds(), Entity::isAlive)) {
            // The broad-phase box is only an optimisation. Exact membership is
            // delegated to the field's immutable shape, just like concentration
            // sampling and public isInside queries.
            if (!field.isInside(target.getBoundingBox().getCenter())) continue;
            target.push(push.x, push.y, push.z);
        }
    }

    /** Converts average field energy to the per-tick movement correction. */
    public static double forceFor(double averageEnergy) {
        if (!Double.isFinite(averageEnergy) || averageEnergy <= 0.0) return 0.0;
        return averageEnergy * FORCE_SCALE;
    }

    /** Returns the direction-only push vector used by the runtime payload. */
    public static Vec3 pushFor(Vec3 direction, double averageEnergy) {
        if (direction == null || direction.lengthSqr() < DIRECTION_EPSILON) return Vec3.ZERO;
        return direction.normalize().scale(forceFor(averageEnergy));
    }
}
