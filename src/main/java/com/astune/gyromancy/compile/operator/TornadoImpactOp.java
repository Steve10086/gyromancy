package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A non-destructive wind burst released when a tornado ball strikes a block.
 * It never calls the level explosion API, so it cannot damage entities or blocks.
 */
public final class TornadoImpactOp extends TriggerOp {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "tornado_impact");
    public static final Codec<TornadoImpactOp> CODEC = Codec.unit(TornadoImpactOp::new);

    static final double PUSH_RADIUS_PER_SIZE = 2.0;
    static final double PUSH_FORCE_PER_SIZE = 0.45;
    private static final double MIN_DISTANCE = 1.0E-4;

    @Override
    public ResourceLocation typeId() {
        return ID;
    }

    @Override
    protected Codec<TornadoImpactOp> codec() {
        return CODEC;
    }

    @Override
    protected boolean shouldTrigger(EntityTickContext ctx) {
        return ctx.hasBlockImpact();
    }

    @Override
    protected void trigger(EntityTickContext ctx) {
        if (!ctx.isClientSide()) {
            double radius = ctx.size() * PUSH_RADIUS_PER_SIZE;
            if (radius > 0.0) pushNearbyEntities(ctx, radius);
        }
        ctx.discard();
    }

    private static void pushNearbyEntities(EntityTickContext ctx, double radius) {
        Vec3 center = ctx.position();
        double maxDistanceSqr = radius * radius;
        AABB searchBox = ctx.bounds().inflate(radius);
        for (Entity target : ctx.level().getEntities(ctx.owner().entity(), searchBox,
                entity -> entity.isAlive())) {
            Vec3 offset = target.getBoundingBox().getCenter().subtract(center);
            double distanceSqr = offset.lengthSqr();
            if (distanceSqr > maxDistanceSqr) continue;

            Vec3 direction = outwardDirection(offset, ctx.velocity());
            if (direction.lengthSqr() == 0.0) continue;
            double distance = Math.sqrt(distanceSqr);
            double force = ctx.size() * PUSH_FORCE_PER_SIZE
                    * Math.max(0.0, 1.0 - distance / radius);
            target.push(direction.x * force, direction.y * force, direction.z * force);
        }
    }

    private static Vec3 outwardDirection(Vec3 offset, Vec3 velocity) {
        if (offset.lengthSqr() >= MIN_DISTANCE * MIN_DISTANCE) return offset.normalize();
        if (velocity.lengthSqr() >= MIN_DISTANCE * MIN_DISTANCE) return velocity.normalize();
        return Vec3.ZERO;
    }
}
