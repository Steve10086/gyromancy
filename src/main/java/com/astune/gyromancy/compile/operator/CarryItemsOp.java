package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.util.MagicBallGeometry;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class CarryItemsOp extends OnEntityTickOp {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "carry_items");
    public static final Codec<CarryItemsOp> CODEC = Codec.unit(CarryItemsOp::new);
    private final Set<UUID> trackedItems = new HashSet<>();

    @Override
    public ResourceLocation typeId() {
        return ID;
    }

    @Override
    protected Codec<CarryItemsOp> codec() {
        return CODEC;
    }

    @Override
    public void onEntityTick(EntityTickContext ctx) {
        double radius = ctx.targetSize() / 2.0;
        Vec3 center = ctx.position().add(0.0, radius, 0.0);

        if (ctx.level() instanceof ServerLevel server) {
            for (UUID uuid : trackedItems) {
                Entity entity = server.getEntity(uuid);
                if (entity instanceof ItemEntity item && !MagicBallGeometry.inSphere(ctx.position(), item.position(), radius)) {
                    item.setNoGravity(false);
                }
            }
        }

        for (ItemEntity item : ctx.level().getEntitiesOfClass(ItemEntity.class,
                ctx.bounds().inflate(0.25), Entity::isAlive)) {
            if (!MagicBallGeometry.inSphere(ctx.position(), item.position(), radius)) continue;
            trackedItems.add(item.getUUID());
            item.setNoGravity(true);
            double angle = ctx.tickCount() * 0.08 + item.getUUID().getLeastSignificantBits() * 0.0001;
            double orbitRadius = radius * 2.0 / 3.0;
            Vec3 target = center.add(Math.cos(angle) * orbitRadius, 0.0, Math.sin(angle) * orbitRadius);
            item.setDeltaMovement(target.subtract(item.position()).scale(0.2));
        }
    }

    public void releaseItems(Level level) {
        for (UUID uuid : trackedItems) {
            Entity entity = level instanceof ServerLevel server ? server.getEntity(uuid) : null;
            if (entity instanceof ItemEntity item) item.setNoGravity(false);
        }
        trackedItems.clear();
    }

    public Set<UUID> trackedItems() {
        return trackedItems;
    }
}
