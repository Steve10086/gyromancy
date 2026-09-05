package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Pulls nearby entities toward, or pushes them away from, the owning tornado ball.
 * Ball size controls only the effect range and the radial target speed. Both the
 * radial and orbital components are bounded velocity corrections applied through
 * {@link Entity#push}, never continuously accumulated accelerations.
 */
public final class TornadoAttractionOp extends OnEntityTickOp {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "tornado_attraction");
    static final double RANGE_PER_SIZE = 5.0;
    static final double MAX_RADIAL_SPEED_PER_SIZE = 0.20;
    static final double MAX_VELOCITY_CORRECTION = 0.18;
    static final double NON_ITEM_FORCE_MULTIPLIER = 0.10;
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

            double correctionLimit = maxVelocityCorrection(target instanceof ItemEntity);
            Vec3 radialDirection = radialOffset.scale(1.0 / distance);
            double radialVelocity = target.getDeltaMovement().dot(radialDirection);
            double targetRadialVelocity = targetRadialVelocity(
                    towardsCenter, ctx.size(), distance, radius, correctionLimit);
            Vec3 radialPush = radialDirection.scale(radialCorrection(
                    radialVelocity, targetRadialVelocity, correctionLimit));
            Vec3 rotationalPush = rotationalCorrection(
                    target.getDeltaMovement(), radialOffset, rotationAxis, angularVelocity, correctionLimit);
            Vec3 push = limitCorrection(radialPush.add(rotationalPush), correctionLimit);
            target.push(push.x, push.y, push.z);
        }
    }

    /**
     * Computes a radial speed that can be braked to zero before reaching the
     * center (or range edge). Its sign is relative to the outward radial axis.
     */
    static double targetRadialVelocity(boolean towardsCenter, float ballSize,
                                       double distance, double radius) {
        return targetRadialVelocity(towardsCenter, ballSize, distance, radius,
                MAX_VELOCITY_CORRECTION);
    }

    static double targetRadialVelocity(boolean towardsCenter, float ballSize,
                                       double distance, double radius, double correctionLimit) {
        if (ballSize <= 0.0F || radius <= 0.0 || distance < 0.0 || distance > radius
                || correctionLimit <= 0.0) return 0.0;

        double remainingDistance = towardsCenter ? distance : radius - distance;
        double stoppingSpeed = Math.sqrt(2.0 * correctionLimit * remainingDistance);
        double speed = Math.min(ballSize * MAX_RADIAL_SPEED_PER_SIZE, stoppingSpeed);
        return towardsCenter ? -speed : speed;
    }

    /** Applies only an impulse through {@code push}; entity velocity is never assigned directly. */
    static double radialCorrection(double currentRadialVelocity, double targetRadialVelocity) {
        return radialCorrection(currentRadialVelocity, targetRadialVelocity,
                MAX_VELOCITY_CORRECTION);
    }

    static double radialCorrection(double currentRadialVelocity, double targetRadialVelocity,
                                   double correctionLimit) {
        double correction = targetRadialVelocity - currentRadialVelocity;
        return Math.clamp(correction, -correctionLimit, correctionLimit);
    }

    /**
     * Corrects only the component of movement in the configured orbital direction.
     * {@code angularVelocity} describes the desired angular speed, not a force to
     * add every tick. Consequently, reaching the target speed produces no push and
     * an overly fast entity receives a braking push in the opposite direction.
     */
    static Vec3 rotationalCorrection(Vec3 currentVelocity, Vec3 centerToTarget,
                                     Vec3 rotationAxis, double angularVelocity) {
        return rotationalCorrection(currentVelocity, centerToTarget, rotationAxis, angularVelocity,
                MAX_VELOCITY_CORRECTION);
    }

    static Vec3 rotationalCorrection(Vec3 currentVelocity, Vec3 centerToTarget,
                                     Vec3 rotationAxis, double angularVelocity,
                                     double correctionLimit) {
        Vec3 desiredVelocity = targetRotationalVelocity(centerToTarget, rotationAxis, angularVelocity);
        double desiredSpeed = desiredVelocity.length();
        if (desiredSpeed < MIN_DISTANCE) return Vec3.ZERO;

        Vec3 direction = desiredVelocity.scale(1.0 / desiredSpeed);
        double currentSpeed = currentVelocity.dot(direction);
        double correction = radialCorrection(currentSpeed, desiredSpeed, correctionLimit);
        return Math.abs(correction) < MIN_DISTANCE ? Vec3.ZERO : direction.scale(correction);
    }

    static Vec3 targetRotationalVelocity(Vec3 centerToTarget, Vec3 rotationAxis,
                                         double angularVelocity) {
        Vec3 axis = normalizedAxis(rotationAxis);
        return axis.cross(centerToTarget).scale(angularVelocity);
    }

    /**
     * Items receive the full effect. Every other entity, deliberately including
     * players, receives one tenth of that force.
     */
    static double maxVelocityCorrection(boolean itemEntity) {
        return itemEntity ? MAX_VELOCITY_CORRECTION
                : MAX_VELOCITY_CORRECTION * NON_ITEM_FORCE_MULTIPLIER;
    }

    private static Vec3 limitCorrection(Vec3 correction, double correctionLimit) {
        double correctionLength = correction.length();
        return correctionLength <= correctionLimit
                ? correction
                : correction.scale(correctionLimit / correctionLength);
    }

    private static Vec3 normalizedAxis(Vec3 axis) {
        return axis == null || axis.lengthSqr() < MIN_DISTANCE * MIN_DISTANCE
                ? DEFAULT_ROTATION_AXIS
                : axis.normalize();
    }
}
