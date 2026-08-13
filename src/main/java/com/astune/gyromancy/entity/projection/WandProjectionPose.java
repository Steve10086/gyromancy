package com.astune.gyromancy.entity.projection;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;

/** Shared target-pose geometry for projections controlled by a wand. */
public final class WandProjectionPose {
    /** Places the projection in front of the held wand instead of the view center. */
    public static final double RIGHT_OFFSET = 0.5;

    private WandProjectionPose() {}

    public static Vec3 targetCenter(Vec3 eyePosition, Vec3 viewDirection,
                                    double forwardDistance, float viewYaw,
                                    boolean mirrorOffset) {
        Vec3 view = viewDirection.normalize();
        return eyePosition
                .add(view.scale(forwardDistance))
                .add(rightDirection(view, viewYaw)
                        .scale(mirrorOffset ? -RIGHT_OFFSET : RIGHT_OFFSET));
    }

    public static boolean mirrorForHand(HumanoidArm mainArm, InteractionHand hand) {
        HumanoidArm actualArm = hand == InteractionHand.MAIN_HAND
                ? mainArm : mainArm.getOpposite();
        return actualArm == HumanoidArm.LEFT;
    }

    /**
     * Returns the horizontal right vector. Using the horizontal projection of
     * the view keeps the wand offset stable while looking up or down.
     */
    public static Vec3 rightDirection(Vec3 viewDirection, float viewYaw) {
        Vec3 horizontalView = new Vec3(viewDirection.x, 0.0, viewDirection.z);
        if (horizontalView.lengthSqr() < 1.0E-8) {
            double yaw = Math.toRadians(viewYaw);
            horizontalView = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        } else {
            horizontalView = horizontalView.normalize();
        }
        return horizontalView.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
    }
}
