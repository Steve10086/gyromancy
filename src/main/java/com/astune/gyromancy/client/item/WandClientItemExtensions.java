package com.astune.gyromancy.client.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/** First-person-only hand transform for the wand's held-use action. */
public final class WandClientItemExtensions implements IClientItemExtensions {
    public static final WandClientItemExtensions INSTANCE = new WandClientItemExtensions();

    private WandClientItemExtensions() {}

    @Override
    public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player,
                                           HumanoidArm arm, ItemStack stack,
                                           float partialTick, float equippedProgress,
                                           float swingProgress) {
        if (!isUsingThisHand(player, arm, stack)) return false;

        float usingTicks = player.getTicksUsingItem() + partialTick;
        float extensionProgress = WandUseAnimation.extensionProgress(usingTicks);
        poseStack.translate(
                0.0F,
                WandUseAnimation.lift(extensionProgress)
                        + WandUseAnimation.verticalOffset(usingTicks),
                -WandUseAnimation.forwardExtension(usingTicks));
        poseStack.mulPose(Axis.XP.rotationDegrees(
                -WandUseAnimation.pitchDegrees(extensionProgress)));

        // Keep vanilla's base arm/equip transform and add this action on top.
        return false;
    }

    private static boolean isUsingThisHand(LocalPlayer player, HumanoidArm arm,
                                           ItemStack stack) {
        if (!player.isUsingItem() || player.getUseItemRemainingTicks() <= 0
                || player.getUseItem().getItem() != stack.getItem()) {
            return false;
        }
        HumanoidArm usingArm = player.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                ? player.getMainArm()
                : player.getMainArm().getOpposite();
        return arm == usingArm;
    }
}
