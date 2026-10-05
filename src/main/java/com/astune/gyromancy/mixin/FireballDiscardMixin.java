package com.astune.gyromancy.mixin;

import com.astune.gyromancy.entity.ball.FireballEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bridges the final Entity.discard() entry point to the fireball's lifecycle classification. */
@Mixin(Entity.class)
public abstract class FireballDiscardMixin {
    @Inject(method = "discard", at = @At("HEAD"), cancellable = true)
    private void gyromancy$onDiscard(CallbackInfo callback) {
        if ((Object) this instanceof FireballEntity fireball
                && fireball.onDiscardRequested()) {
            callback.cancel();
        }
    }
}
