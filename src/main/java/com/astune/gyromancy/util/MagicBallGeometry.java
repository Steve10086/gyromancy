package com.astune.gyromancy.util;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class MagicBallGeometry {
    private MagicBallGeometry() {}

    public static boolean inSphere(Vec3 ballPosition, Vec3 target, double radius) {
        return ballPosition.add(0.0, radius, 0.0).distanceToSqr(target) <= radius * radius;
    }

    public static List<BlockPos> containedPositions(Vec3 ballPosition, float size) {
        double r = size / 2.0;
        Vec3 center = ballPosition.add(0.0, r, 0.0);
        AABB box = new AABB(center.x - r, center.y - r, center.z - r,
                center.x + r, center.y + r, center.z + r);
        List<BlockPos> positions = new ArrayList<>();
        BlockPos.betweenClosedStream(box)
                .map(BlockPos::immutable)
                .filter(pos -> center.distanceToSqr(pos.getCenter()) <= r * r)
                .forEach(positions::add);
        if (positions.isEmpty()) positions.add(BlockPos.containing(center));
        return positions;
    }

    /**
     * Every block whose voxel touches the sphere around an explicit centre.
     * Touching counts even when the block's centre is outside the sphere, so
     * small radii still cover the blocks they overlap.
     */
    public static List<BlockPos> positionsInSphere(Vec3 center, double radius) {
        if (center == null || !Double.isFinite(radius) || radius <= 0.0) return List.of();
        AABB box = new AABB(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius).inflate(1.0);
        List<BlockPos> positions = new ArrayList<>();
        BlockPos.betweenClosedStream(box)
                .map(BlockPos::immutable)
                .filter(pos -> touchesSphere(center, radius, pos))
                .forEach(positions::add);
        return positions;
    }

    private static boolean touchesSphere(Vec3 center, double radius, BlockPos pos) {
        double closestX = Mth.clamp(center.x, pos.getX(), pos.getX() + 1.0);
        double closestY = Mth.clamp(center.y, pos.getY(), pos.getY() + 1.0);
        double closestZ = Mth.clamp(center.z, pos.getZ(), pos.getZ() + 1.0);
        double dx = center.x - closestX;
        double dy = center.y - closestY;
        double dz = center.z - closestZ;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    public static double volume(float size) {
        double r = size / 2.0;
        return 4.0 / 3.0 * Math.PI * r * r * r;
    }

    public static double sizeForVolume(double volume, float minSize) {
        return Math.cbrt(Math.max(volume, volume(minSize)) * 3.0 / (4.0 * Math.PI)) * 2.0;
    }
}
