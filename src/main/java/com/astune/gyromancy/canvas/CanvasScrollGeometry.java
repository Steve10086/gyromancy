package com.astune.gyromancy.canvas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/** Placement and model transform for the authored canvas_plot geometry. */
public final class CanvasScrollGeometry {
    private static final AABB MODEL_BOUNDS = new AABB(
            0.75 / 16, -0.25 / 16, 0.5 / 16,
            5.25 / 16, 4.25 / 16, 15.5 / 16);

    private CanvasScrollGeometry() {}

    public static float lengthScale(int canvasHeight) {
        return (float) (canvasHeight / MODEL_BOUNDS.getZsize());
    }

    public static Vec3 origin(BlockPos placement, Direction facing) {
        // The clicked face is half a block behind the placement block centre.
        // Use its local corner, retaining the model's authored in-plane margins.
        // Only its minimum thickness coordinate is brought flush to the face.
        return Vec3.atCenterOf(placement)
                .relative(facing, -0.5 - MODEL_BOUNDS.minY)
                .add(CanvasOrientation.widthAxis(facing).scale(0.5))
                .subtract(CanvasOrientation.heightAxis(facing).scale(0.5));
    }

    public static Quaternionf rotation(Direction facing) {
        Vec3 x = CanvasOrientation.widthAxis(facing).scale(-1);
        Vec3 y = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 z = CanvasOrientation.heightAxis(facing);
        return new Quaternionf().setFromNormalized(new Matrix3f(
                (float) x.x, (float) x.y, (float) x.z,
                (float) y.x, (float) y.y, (float) y.z,
                (float) z.x, (float) z.y, (float) z.z));
    }

    public static AABB bounds(BlockPos placement, Direction facing, int canvasHeight) {
        Vec3 width = CanvasOrientation.widthAxis(facing);
        Vec3 height = CanvasOrientation.heightAxis(facing);
        Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 modelCenter = MODEL_BOUNDS.getCenter();
        double length = MODEL_BOUNDS.getZsize() * lengthScale(canvasHeight);
        Vec3 center = origin(placement, facing)
                .subtract(width.scale(modelCenter.x))
                .add(normal.scale(modelCenter.y))
                .add(height.scale(modelCenter.z * lengthScale(canvasHeight)));
        return AABB.ofSize(center,
                Math.abs(width.x) * MODEL_BOUNDS.getXsize() + Math.abs(normal.x) * MODEL_BOUNDS.getYsize() + Math.abs(height.x) * length,
                Math.abs(width.y) * MODEL_BOUNDS.getXsize() + Math.abs(normal.y) * MODEL_BOUNDS.getYsize() + Math.abs(height.y) * length,
                Math.abs(width.z) * MODEL_BOUNDS.getXsize() + Math.abs(normal.z) * MODEL_BOUNDS.getYsize() + Math.abs(height.z) * length);
    }
}
