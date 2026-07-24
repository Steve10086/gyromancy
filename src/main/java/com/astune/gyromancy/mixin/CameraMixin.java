package com.astune.gyromancy.mixin;

import com.astune.gyromancy.client.PaintCameraController;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin implements PaintCameraController.CameraPoseAccess {

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot, float roll);

    @Inject(method = "setup", at = @At("TAIL"))
    private void gyromancy$applyPaintCameraPose(BlockGetter level, Entity entity, boolean detached,
                                                boolean thirdPersonReverse, float partialTick,
                                                CallbackInfo ci) {
        PaintCameraController.applyCameraPose((Camera) (Object) this);
    }

    @Override
    public void gyromancy$setPaintCameraPose(Vec3 position, float yaw, float pitch) {
        this.setPosition(position);
        this.setRotation(yaw, pitch, 0.0F);
    }
}
