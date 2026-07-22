package com.astune.gyromancy.util;

import net.minecraft.core.BlockPos;
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

    public static double volume(float size) {
        double r = size / 2.0;
        return 4.0 / 3.0 * Math.PI * r * r * r;
    }

    public static double sizeForVolume(double volume, float minSize) {
        return Math.cbrt(Math.max(volume, volume(minSize)) * 3.0 / (4.0 * Math.PI)) * 2.0;
    }
}
