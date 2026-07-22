package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class ExplosionOp extends TriggerOp {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "explosion");
    public static final Codec<ExplosionOp> CODEC = Codec.unit(ExplosionOp::new);
    public static final String EXPLOSION_POWER_KEY = "explosionPower";
    public static final String MAX_SIZE_KEY = "maxSize";

    @Override
    public ResourceLocation typeId() {
        return ID;
    }

    @Override
    protected Codec<ExplosionOp> codec() {
        return CODEC;
    }

    @Override
    protected boolean shouldTrigger(EntityTickContext ctx) {
        burnEntitiesInPath(ctx);
        return ctx.hasImpact() || ctx.size() > ctx.doubleValue(MAX_SIZE_KEY, Double.POSITIVE_INFINITY);
    }

    @Override
    public void trigger(EntityTickContext ctx) {
        if (!ctx.isClientSide()) {
            float power = ctx.floatValue(EXPLOSION_POWER_KEY, 1.5f);
            ctx.level().explode(ctx.owner(), ctx.owner().getX(), ctx.owner().getY(), ctx.owner().getZ(),
                    power, true, Level.ExplosionInteraction.MOB);
            igniteNearbyBlocks(ctx, power);
        }
        ctx.discard();
    }

    private static void burnEntitiesInPath(EntityTickContext ctx) {
        var searchBox = ctx.bounds().expandTowards(ctx.velocity()).inflate(0.3);
        ctx.level().getEntitiesOfClass(Entity.class, searchBox, e -> e != ctx.owner()).forEach(target -> {
            if (target instanceof ItemEntity || target instanceof AbstractArrow) {
                target.setRemainingFireTicks(200);
            }
        });
    }

    private static void igniteNearbyBlocks(EntityTickContext ctx, float power) {
        int radius = Math.max(1, (int)Math.ceil(power));
        BlockPos center = ctx.owner().blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (!ctx.level().isEmptyBlock(pos)) continue;
            BlockState fire = BaseFireBlock.getState(ctx.level(), pos);
            if (fire.canSurvive(ctx.level(), pos)) ctx.level().setBlock(pos, fire, 3);
        }
    }
}
