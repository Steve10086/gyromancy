package com.astune.gyromancy.mixin;

import com.astune.gyromancy.item.WandItem;
import net.minecraft.client.player.LocalPlayer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restores normal movement input while a wand is being used. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(
            method = "aiStep",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/player/Input;forwardImpulse:F",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER))
    private void gyromancy$restoreWandMovementInput(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (!player.isUsingItem()
                || player.isPassenger()
                || !(player.getUseItem().getItem() instanceof WandItem)) {
            return;
        }

        // LocalPlayer applies a 0.2 multiplier to both impulses for every
        // actively used item. Undo that multiplier only for the wand. The
        // surrounding isUsingItem checks still keep sprinting disabled.
        player.input.leftImpulse *= 5.0F;
        player.input.forwardImpulse *= 5.0F;
    }
}
