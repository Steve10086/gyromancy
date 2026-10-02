package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class RemoveOnHitOp extends TriggerOp {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "remove_on_hit");
    public static final Codec<WaterBurstOp> CODEC = Codec.unit(WaterBurstOp::new);

    @Override
    public ResourceLocation typeId() {
        return ID;
    }

    @Override
    protected Codec<WaterBurstOp> codec() {
        return CODEC;
    }

    @Override
    protected boolean shouldTrigger(EntityTickContext ctx) {
        Vec3 velocity = ctx.velocity();
        if(velocity.lengthSqr() <= 1e-8) return false;
        HitResult blockHit = ctx.level().clip(new ClipContext(ctx.position(), ctx.position().add(velocity),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx.owner().entity()));
        if (blockHit.getType() != HitResult.Type.MISS) return true;
        return !ctx.level().getEntitiesOfClass(LivingEntity.class,
                ctx.bounds().expandTowards(velocity).inflate(0.1), LivingEntity::isAlive).isEmpty();
    }

    @Override
    protected void trigger(EntityTickContext ctx) {
        ctx.discard();
    }
}
