package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.util.MagicBallGeometry;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CarryItemsOp extends OnEntityTickOp {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "carry_items");
    public static final Codec<CarryItemsOp> CODEC = Codec.unit(CarryItemsOp::new);
    private static final double ORBIT_ANGULAR_SPEED = 0.08;
    private final Set<UUID> trackedItems = new HashSet<>();
    private final Map<UUID, OrbitState> orbitStates = new HashMap<>();
    private boolean clientStateDirty;

    @Override
    public ResourceLocation typeId() {
        return ID;
    }

    @Override
    protected Codec<CarryItemsOp> codec() {
        return CODEC;
    }

    @Override
    public boolean ticksOnClient() {
        return true;
    }

    @Override
    public boolean hasClientState() {
        return true;
    }

    @Override
    public boolean consumeClientStateDirty() {
        boolean dirty = clientStateDirty;
        clientStateDirty = false;
        return dirty;
    }

    @Override
    public void onEntityTick(EntityTickContext ctx) {
        double radius = ctx.targetSize() / 2.0;
        Vec3 ownerMovement = ctx.owner().getDeltaMovement();
        Vec3 previousPosition = ctx.position().subtract(ownerMovement);
        Vec3 nextPosition = ctx.position().add(ownerMovement);
        Vec3 center = ctx.position().add(0.0, radius, 0.0);
        double orbitRadius = radius * 2.0 / 3.0;
        Vec3 targetCenter = center.add(ownerMovement);

        if (ctx.isClientSide()) {
            tickClient(ctx, targetCenter, orbitRadius);
            return;
        }

        boolean changed = false;
        if (ctx.level() instanceof ServerLevel server) {
            for (Iterator<UUID> iterator = trackedItems.iterator(); iterator.hasNext();) {
                UUID uuid = iterator.next();
                Entity entity = server.getEntity(uuid);
                if (!(entity instanceof ItemEntity item)
                        || !item.isAlive()
                        || !inCarryRange(previousPosition, ctx.position(), nextPosition, item.position(), radius)) {
                    if (entity instanceof ItemEntity item) item.setNoGravity(false);
                    orbitStates.remove(uuid);
                    iterator.remove();
                    changed = true;
                }
            }
        }

        for (ItemEntity item : ctx.level().getEntitiesOfClass(ItemEntity.class,
                ctx.bounds().expandTowards(ownerMovement).expandTowards(ownerMovement.reverse()).inflate(0.25),
                Entity::isAlive)) {
            if (!inCarryRange(previousPosition, ctx.position(), nextPosition, item.position(), radius)) continue;
            boolean added = trackedItems.add(item.getUUID());
            OrbitState state = orbitStates.get(item.getUUID());
            if (state == null) {
                state = OrbitState.create(item, center, ctx.tickCount());
                orbitStates.put(item.getUUID(), state);
                changed = true;
            }
            if (state.entityId != item.getId()) {
                state = new OrbitState(item.getId(), state.startPhase, state.startTick, state.direction);
                orbitStates.put(item.getUUID(), state);
                changed = true;
            }
            if (added) changed = true;
            item.setNoGravity(true);
            item.setDeltaMovement(targetPosition(ctx, state, targetCenter, orbitRadius).subtract(item.position()));
        }

        if (changed) clientStateDirty = true;
    }

    private static boolean inCarryRange(Vec3 previousPosition, Vec3 currentPosition, Vec3 nextPosition,
                                        Vec3 itemPosition, double radius) {
        return MagicBallGeometry.inSphere(previousPosition, itemPosition, radius)
                || MagicBallGeometry.inSphere(currentPosition, itemPosition, radius)
                || MagicBallGeometry.inSphere(nextPosition, itemPosition, radius);
    }

    private void tickClient(EntityTickContext ctx, Vec3 center, double orbitRadius) {
        for (OrbitState state : orbitStates.values()) {
            Entity entity = ctx.level().getEntity(state.entityId);
            if (!(entity instanceof ItemEntity item) || !item.isAlive()) continue;
            Vec3 target = targetPosition(ctx, state, center, orbitRadius);
            item.setNoGravity(true);
            item.setPos(target);
            item.setDeltaMovement(orbitVelocity(ctx, state, orbitRadius));
        }
    }

    private static double currentPhase(ItemEntity item, Vec3 center) {
        Vec3 offset = item.position().subtract(center);
        if (offset.x * offset.x + offset.z * offset.z < 1.0E-6) {
            return item.getUUID().getLeastSignificantBits() * 0.0001;
        }
        return Math.atan2(offset.z, offset.x);
    }

    private static Vec3 targetPosition(EntityTickContext ctx, OrbitState state, Vec3 center, double orbitRadius) {
        double phase = state.phaseAt(ctx.tickCount());
        return center.add(Math.cos(phase) * orbitRadius, 0.0, Math.sin(phase) * orbitRadius);
    }

    private static Vec3 orbitVelocity(EntityTickContext ctx, OrbitState state, double orbitRadius) {
        double phase = state.phaseAt(ctx.tickCount());
        double tangentX = -Math.sin(phase) * orbitRadius * ORBIT_ANGULAR_SPEED * state.direction;
        double tangentZ = Math.cos(phase) * orbitRadius * ORBIT_ANGULAR_SPEED * state.direction;
        return ctx.velocity().add(tangentX, 0.0, tangentZ);
    }

    public void releaseItems(Level level) {
        for (UUID uuid : trackedItems) {
            Entity entity = level instanceof ServerLevel server ? server.getEntity(uuid) : null;
            if (entity instanceof ItemEntity item) item.setNoGravity(false);
        }
        trackedItems.clear();
        orbitStates.clear();
    }

    public Set<UUID> trackedItems() {
        return trackedItems;
    }

    @Override
    public CompoundTag saveClientState() {
        CompoundTag tag = new CompoundTag();
        ListTag items = new ListTag();
        trackedItems.stream()
                .sorted()
                .map(uuid -> Map.entry(uuid, orbitStates.get(uuid)))
                .filter(entry -> entry.getValue() != null)
                .forEach(entry -> {
                    CompoundTag item = new CompoundTag();
                    item.putUUID("Uuid", entry.getKey());
                    entry.getValue().save(item);
                    items.add(item);
                });
        tag.put("Items", items);
        return tag;
    }

    @Override
    public void loadClientState(Level level, CompoundTag tag) {
        Set<Integer> oldIds = orbitStates.values().stream()
                .map(state -> state.entityId)
                .collect(java.util.stream.Collectors.toSet());
        trackedItems.clear();
        orbitStates.clear();
        for (Tag value : tag.getList("Items", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag)value;
            if (!item.hasUUID("Uuid")) continue;
            UUID uuid = item.getUUID("Uuid");
            trackedItems.add(uuid);
            OrbitState state = OrbitState.load(item);
            orbitStates.put(uuid, state);
            oldIds.remove(state.entityId);
        }
        for (int id : oldIds) {
            Entity entity = level.getEntity(id);
            if (entity instanceof ItemEntity item) item.setNoGravity(false);
        }
    }

    @Override
    public void onOwnerRemoved(Level level) {
        releaseItems(level);
    }

    private record OrbitState(int entityId, double startPhase, int startTick, double direction) {
        static OrbitState create(ItemEntity item, Vec3 center, int tick) {
            double direction = (item.getUUID().getLeastSignificantBits() & 1L) == 0L ? 1.0 : -1.0;
            return new OrbitState(item.getId(), currentPhase(item, center), tick, direction);
        }

        double phaseAt(int tick) {
            return startPhase + direction * ORBIT_ANGULAR_SPEED * Math.max(0, tick - startTick + 1);
        }

        void save(CompoundTag tag) {
            tag.putInt("Id", entityId);
            tag.putDouble("StartPhase", startPhase);
            tag.putInt("StartTick", startTick);
            tag.putDouble("Direction", direction);
        }

        static OrbitState load(CompoundTag tag) {
            double direction = tag.contains("Direction") ? tag.getDouble("Direction") : 1.0;
            return new OrbitState(tag.getInt("Id"), tag.getDouble("StartPhase"),
                    tag.getInt("StartTick"), direction);
        }
    }
}
