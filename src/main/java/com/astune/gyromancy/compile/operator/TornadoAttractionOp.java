package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Pulls nearby entities toward, or pushes them away from, the owning tornado ball.
 * Both the range and force scale with the ball's live size.
 */
public final class TornadoAttractionOp extends OnEntityTickOp {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "tornado_attraction");
    static final double RANGE_PER_SIZE = 5.0;
    static final double MAX_FORCE_PER_SIZE = 0.18;
    static final double CURVE_FORCE_PER_SIZE = 0.25;
    static final double DEFAULT_ANGULAR_VELOCITY = 0.04;
    private static final Vec3 DEFAULT_ROTATION_AXIS = new Vec3(0.0, 1.0, 0.0);
    private static final double MIN_DISTANCE = 1.0E-4;

    public static final Codec<TornadoAttractionOp> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("towards_center").forGetter(TornadoAttractionOp::towardsCenter),
            Codec.DOUBLE.optionalFieldOf("rotation_axis_x", 0.0).forGetter(op -> op.rotationAxis.x),
            Codec.DOUBLE.optionalFieldOf("rotation_axis_y", 1.0).forGetter(op -> op.rotationAxis.y),
            Codec.DOUBLE.optionalFieldOf("rotation_axis_z", 0.0).forGetter(op -> op.rotationAxis.z),
            Codec.DOUBLE.optionalFieldOf("angular_velocity", DEFAULT_ANGULAR_VELOCITY)
                    .forGetter(TornadoAttractionOp::angularVelocity)
    ).apply(instance, (towardsCenter, axisX, axisY, axisZ, angularVelocity) ->
            new TornadoAttractionOp(towardsCenter, new Vec3(axisX, axisY, axisZ), angularVelocity)));

    private final boolean towardsCenter;
    private final Vec3 rotationAxis;
    private final double angularVelocity;

    /**
     * @param towardsCenter {@code true} to pull toward the ball center,
     *                      {@code false} to push away from it
     */
    public TornadoAttractionOp(boolean towardsCenter) {
        this(towardsCenter, DEFAULT_ROTATION_AXIS, DEFAULT_ANGULAR_VELOCITY);
    }

    /**
     * @param towardsCenter {@code true} to pull toward the ball center,
     *                      {@code false} to push away from it
     * @param rotationAxis rotation axis and direction, interpreted with the right-hand rule
     * @param angularVelocity angular speed in radians per tick; a negative value reverses rotation
     */
    public TornadoAttractionOp(boolean towardsCenter, Vec3 rotationAxis, double angularVelocity) {
        this.towardsCenter = towardsCenter;
        this.rotationAxis = normalizedAxis(rotationAxis);
        this.angularVelocity = angularVelocity;
    }

    public boolean towardsCenter() {
        return towardsCenter;
    }

    public Vec3 rotationAxis() {
        return rotationAxis;
    }

    public double angularVelocity() {
        return angularVelocity;
    }

    @Override
    public ResourceLocation typeId() {
        return ID;
    }

    @Override
    protected Codec<TornadoAttractionOp> codec() {
        return CODEC;
    }

    @Override
    public void onEntityTick(EntityTickContext ctx) {
        if (ctx.isClientSide()) return;

        double radius = ctx.size() * RANGE_PER_SIZE;
        if (radius <= 0.0) return;

        Vec3 center = ctx.position();
        AABB searchBox = ctx.bounds().inflate(radius);
        for (Entity target : ctx.level().getEntities(ctx.owner(), searchBox,
                entity -> entity.isAlive())) {
            Vec3 radialOffset = target.getBoundingBox().getCenter().subtract(center);
            double distance = radialOffset.length();
            if (distance >= radius || distance < MIN_DISTANCE) continue;

            double force = forceMagnitude(ctx.size(), distance);
            Vec3 radialDirection = radialOffset.scale(1.0 / distance);
            if (towardsCenter) radialDirection = radialDirection.scale(-1.0);
            Vec3 radialPush = radialDirection.scale(force);
            Vec3 rotationalPush = rotationalPush(radialOffset, rotationAxis, angularVelocity);
            Vec3 push = radialPush.add(rotationalPush);
            target.push(push.x, push.y, push.z);
        }
    }

    static double forceMagnitude(float ballSize, double distance) {
        if (ballSize <= 0.0F || distance < 0.0) return 0.0;
        double maxForce = ballSize * MAX_FORCE_PER_SIZE;
        double normalizedDistance = Math.max(distance / ballSize, MIN_DISTANCE);
        double curveForce = ballSize * CURVE_FORCE_PER_SIZE / Math.sqrt(normalizedDistance);
        return Math.min(maxForce, curveForce);
    }

    static Vec3 rotationalPush(Vec3 centerToTarget, Vec3 rotationAxis, double angularVelocity) {
        Vec3 axis = normalizedAxis(rotationAxis);
        return axis.cross(centerToTarget).scale(angularVelocity);
    }

    private static Vec3 normalizedAxis(Vec3 axis) {
        return axis == null || axis.lengthSqr() < MIN_DISTANCE * MIN_DISTANCE
                ? DEFAULT_ROTATION_AXIS
                : axis.normalize();
    }
}
