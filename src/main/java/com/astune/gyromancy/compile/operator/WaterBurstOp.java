package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.util.MagicBallGeometry;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class WaterBurstOp extends TriggerOp {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "water_burst");
    public static final Codec<WaterBurstOp> CODEC = Codec.unit(WaterBurstOp::new);
    private static final double BURST_PUSH_STRENGTH = 1.2;

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
        if (velocity.lengthSqr() <= 1.0E-8) return false;
        HitResult blockHit = ctx.level().clip(new ClipContext(ctx.position(), ctx.position().add(velocity),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx.owner().entity()));
        if (blockHit.getType() != HitResult.Type.MISS) return true;
        return !ctx.level().getEntitiesOfClass(LivingEntity.class,
                ctx.bounds().expandTowards(velocity).inflate(0.1), LivingEntity::isAlive).isEmpty();
    }

    @Override
    protected void trigger(EntityTickContext ctx) {
        if (!ctx.isClientSide()) {
            pushFrontEntities(ctx);
            fillWithFlowingWater(ctx);
        }
    }

    private static void pushFrontEntities(EntityTickContext ctx) {
        Vec3 direction = ctx.velocity();
        if (direction.lengthSqr() < 1.0E-8) return;
        direction = direction.normalize();
        double radius = ctx.targetSize() / 2.0;
        Vec3 front = ctx.position().add(0.0, radius, 0.0).add(direction.scale(radius));
        double range = Math.max(1.0, radius);
        for (Entity entity : ctx.level().getEntities(ctx.owner().entity(),
                ctx.bounds().inflate(range).expandTowards(direction.scale(range)))) {
            if (!entity.isAlive()
                    || entity instanceof ItemEntity
                    || front.distanceToSqr(entity.getBoundingBox().getCenter()) > range * range) continue;
            entity.push(direction.x * BURST_PUSH_STRENGTH, direction.y * BURST_PUSH_STRENGTH, direction.z * BURST_PUSH_STRENGTH);
        }
    }

    private static void fillWithFlowingWater(EntityTickContext ctx) {
        var flowingWater = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1);
        for (BlockPos pos : MagicBallGeometry.containedPositions(ctx.position(), ctx.targetSize() * 2.0F)) {
            if (ctx.level().isEmptyBlock(pos)) ctx.level().setBlock(pos, flowingWater, 3);
        }
    }
}
