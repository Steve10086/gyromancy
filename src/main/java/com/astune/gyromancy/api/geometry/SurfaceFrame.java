package com.astune.gyromancy.api.geometry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * A persistent two-dimensional coordinate frame embedded in world space.
 * Compiler geometry uses this frame instead of assuming one of six block faces.
 */
public record SurfaceFrame(Vec3 origin, Vec3 axisU, Vec3 axisV, Vec3 normal) {
    public static final double EPSILON = 1.0E-6;

    private static final Codec<Vec3> VEC3_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.DOUBLE.fieldOf("x").forGetter(value -> value.x),
                    Codec.DOUBLE.fieldOf("y").forGetter(value -> value.y),
                    Codec.DOUBLE.fieldOf("z").forGetter(value -> value.z))
                    .apply(instance, Vec3::new));

    public static final Codec<SurfaceFrame> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    VEC3_CODEC.fieldOf("origin").forGetter(SurfaceFrame::origin),
                    VEC3_CODEC.fieldOf("axis_u").forGetter(SurfaceFrame::axisU),
                    VEC3_CODEC.fieldOf("axis_v").forGetter(SurfaceFrame::axisV),
                    VEC3_CODEC.fieldOf("normal").forGetter(SurfaceFrame::normal))
                    .apply(instance, SurfaceFrame::new));

    public SurfaceFrame {
        if (!finite(origin) || !finite(axisU) || !finite(axisV) || !finite(normal)
                || axisU.lengthSqr() < EPSILON || axisV.lengthSqr() < EPSILON
                || normal.lengthSqr() < EPSILON) {
            throw new IllegalArgumentException("Surface frame vectors must be finite and non-zero");
        }
        axisU = axisU.normalize();
        axisV = axisV.normalize();
        normal = normal.normalize();
        if (Math.abs(axisU.dot(axisV)) > EPSILON
                || Math.abs(axisU.dot(normal)) > EPSILON
                || Math.abs(axisV.dot(normal)) > EPSILON
                || Math.abs(Math.abs(axisU.cross(axisV).dot(normal)) - 1.0) > EPSILON) {
            throw new IllegalArgumentException("Surface frame axes must be orthonormal");
        }
    }

    /** Creates the exact coordinate convention used by legacy BlockPos+Direction glyphs. */
    public static SurfaceFrame fromBlockFace(BlockPos pos, Direction face) {
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 point = Vec3.atCenterOf(pos).add(normal.scale(0.5));
        Vec3 origin = normal.scale(point.dot(normal));
        Vec3 axisU = switch (face) {
            case NORTH, SOUTH, UP, DOWN -> new Vec3(1.0, 0.0, 0.0);
            case EAST, WEST -> new Vec3(0.0, 0.0, 1.0);
        };
        Vec3 axisV = face.getAxis().isVertical()
                ? new Vec3(0.0, 0.0, 1.0)
                : new Vec3(0.0, 1.0, 0.0);
        return new SurfaceFrame(origin, axisU, axisV, normal);
    }

    /**
     * Creates an upright free-facing surface. The U axis points to screen-right
     * when the surface is viewed from the side opposite {@code outwardNormal}.
     */
    public static SurfaceFrame facing(Vec3 origin, Vec3 outwardNormal, Vec3 preferredUp) {
        if (!finite(outwardNormal) || outwardNormal.lengthSqr() < EPSILON
                || !finite(preferredUp) || preferredUp.lengthSqr() < EPSILON) {
            throw new IllegalArgumentException("Facing vectors must be finite and non-zero");
        }
        Vec3 normal = outwardNormal.normalize();
        Vec3 axisV = preferredUp.subtract(normal.scale(preferredUp.dot(normal)));
        if (axisV.lengthSqr() < EPSILON) {
            Vec3 fallbackUp = Math.abs(normal.y) < 0.9
                    ? new Vec3(0.0, 1.0, 0.0)
                    : new Vec3(0.0, 0.0, -1.0);
            axisV = fallbackUp.subtract(normal.scale(fallbackUp.dot(normal)));
        }
        axisV = axisV.normalize();
        // The observer is on the side opposite the outward normal and looks
        // along it, so screen-right is normal x screen-up.
        Vec3 axisU = normal.cross(axisV).normalize();
        return new SurfaceFrame(origin, axisU, axisV, normal);
    }

    public Coordinates project(Vec3 worldPosition) {
        Vec3 relative = worldPosition.subtract(origin);
        return new Coordinates(relative.dot(axisU), relative.dot(axisV));
    }

    public Vec3 world(double u, double v) {
        return origin.add(axisU.scale(u)).add(axisV.scale(v));
    }

    public double signedDistance(Vec3 worldPosition) {
        return worldPosition.subtract(origin).dot(normal);
    }

    public boolean isCoplanar(SurfaceFrame other) {
        return isCoplanar(other, 1.0E-5);
    }

    public boolean isCoplanar(SurfaceFrame other, double tolerance) {
        return normal.dot(other.normal) >= 1.0 - tolerance
                && Math.abs(signedDistance(other.origin)) <= tolerance;
    }

    /** Returns the six-direction adapter only when this frame really is axis aligned. */
    public Optional<Direction> axisAlignedDirection() {
        Direction nearest = Direction.getNearest(normal);
        Vec3 cardinal = Vec3.atLowerCornerOf(nearest.getNormal());
        return normal.distanceToSqr(cardinal) <= EPSILON * EPSILON
                ? Optional.of(nearest) : Optional.empty();
    }

    public Direction requireAxisAlignedDirection() {
        return axisAlignedDirection().orElseThrow(() ->
                new IllegalStateException("Surface is not compatible with six-direction coordinates"));
    }

    /**
     * Converts a point on an axis-aligned frame back to the supporting block face.
     * This is the compatibility entry point for APIs which still require BlockPos+Direction.
     */
    public Optional<LegacyBlockFace> legacyBlockFaceAt(Vec3 worldPosition) {
        return axisAlignedDirection().map(face -> new LegacyBlockFace(
                BlockPos.containing(worldPosition.subtract(normal.scale(EPSILON))), face));
    }

    public SurfaceBounds transformBounds(SurfaceBounds bounds, SurfaceFrame target) {
        Vec3[] corners = {
                world(bounds.minU(), bounds.minV()),
                world(bounds.maxU(), bounds.minV()),
                world(bounds.minU(), bounds.maxV()),
                world(bounds.maxU(), bounds.maxV())
        };
        double minU = Double.POSITIVE_INFINITY;
        double maxU = Double.NEGATIVE_INFINITY;
        double minV = Double.POSITIVE_INFINITY;
        double maxV = Double.NEGATIVE_INFINITY;
        for (Vec3 corner : corners) {
            Coordinates coordinates = target.project(corner);
            minU = Math.min(minU, coordinates.u());
            maxU = Math.max(maxU, coordinates.u());
            minV = Math.min(minV, coordinates.v());
            maxV = Math.max(maxV, coordinates.v());
        }
        return new SurfaceBounds(minU, maxU, minV, maxV);
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    public record Coordinates(double u, double v) {}

    public record SurfaceBounds(double minU, double maxU, double minV, double maxV) {
        public double centerU() { return (minU + maxU) * 0.5; }
        public double centerV() { return (minV + maxV) * 0.5; }
        public double area() {
            return Math.max(0.0, maxU - minU) * Math.max(0.0, maxV - minV);
        }
    }

    public record LegacyBlockFace(BlockPos pos, Direction face) {}
}
