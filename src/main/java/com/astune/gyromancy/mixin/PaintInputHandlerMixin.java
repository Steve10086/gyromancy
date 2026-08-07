package com.astune.gyromancy.mixin;

import com.astune.gyromancy.client.PaintCameraController;
import com.astune.gyromancy.item.CompassItem;
import com.astune.painter.api.IPaintProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.astune.painter.client.PaintInputHandler", remap = false)
public abstract class PaintInputHandlerMixin {

    @Inject(method = "onRenderFrame", at = @At("HEAD"))
    private static void gyromancy$refreshPaintCameraHit(RenderFrameEvent.Pre event, CallbackInfo ci) {
        PaintCameraController.updatePointerHitResult(Minecraft.getInstance());
        if (!PaintCameraController.isActive()) {
            CompassItem.updateHitResult(Minecraft.getInstance());
        }
    }

    @Inject(method = "traceHit", at = @At("HEAD"), cancellable = true)
    private static void gyromancy$tracePaintCameraHit(Minecraft minecraft, Vec3 target,
                                                      CallbackInfoReturnable<BlockHitResult> cir) {
        BlockHitResult compassHit = CompassItem.hitForTrace(target);
        if (compassHit != null) {
            cir.setReturnValue(compassHit);
            return;
        }
        BlockHitResult hit = PaintCameraController.hitOnActivePlane(target);
        if (hit != null) {
            cir.setReturnValue(hit);
        }
    }

    @Inject(method = "traceNormalDir", at = @At("HEAD"), cancellable = true)
    private static void gyromancy$tracePaintCameraNormal(Vec3 point, Vec3 normal, Player player,
                                                        CallbackInfoReturnable<BlockHitResult> cir) {
        BlockHitResult compassHit = CompassItem.hitForNormalTrace(point, normal, player);
        if (compassHit != null) {
            cir.setReturnValue(compassHit);
            return;
        }
        BlockHitResult hit = PaintCameraController.hitOnActivePlane(point);
        if (hit != null) {
            cir.setReturnValue(hit);
        }
    }

    @Redirect(
            method = "paintPattern",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/astune/painter/api/IPaintProvider;transformPatternAxes(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/core/Direction;DD)[Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private static Vec3[] gyromancy$transformPaintCameraAxes(IPaintProvider provider, Player player, Vec3 hitLocation,
                                                            Direction direction, double width, double height) {
        Vec3[] axes = PaintCameraController.transformPatternAxes(direction, hitLocation, width, height);
        return axes != null ? axes : provider.transformPatternAxes(player, hitLocation, direction, width, height);
    }
}
