package com.astune.gyromancy.api.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Abstract base class for all pseudo-entities in the Gyromancy mod.
 * Pseudo-entities are lightweight Minecraft Entities that are bound to a magic array
 * and exist purely for visual and effect purposes.
 *
 * <p>They use vanilla entity loading/unloading (saved with chunks), but have:
 * <ul>
 *   <li>No physics ({@code noPhysics = true})</li>
 *   <li>No collision</li>
 *   <li>No AI</li>
 * </ul>
 *
 * Pseudo-entities respond to element system events via the {@code ElementEventBus}.
 */
public abstract class PseudoEntity extends Entity {

    protected BlockPos boundArrayPos;
    protected UUID boundArrayId = UUID.randomUUID();
    protected final List<Object> elementSubscriptions = new ArrayList<>(); // ElementEventSubscription after Phase 2

    protected PseudoEntity(EntityType<? extends PseudoEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    // ── Vanilla Entity lifecycle ──

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        if (tag.contains("BoundArrayX")) {
            int x = tag.getInt("BoundArrayX");
            int y = tag.getInt("BoundArrayY");
            int z = tag.getInt("BoundArrayZ");
            boundArrayPos = new BlockPos(x, y, z);
        }
        if (tag.hasUUID("BoundArrayId")) {
            boundArrayId = tag.getUUID("BoundArrayId");
        }
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        if (boundArrayPos != null) {
            tag.putInt("BoundArrayX", boundArrayPos.getX());
            tag.putInt("BoundArrayY", boundArrayPos.getY());
            tag.putInt("BoundArrayZ", boundArrayPos.getZ());
        }
        tag.putUUID("BoundArrayId", boundArrayId);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        // Subclasses define their own synched data
    }

    @Override
    public void remove(@NotNull RemovalReason reason) {
        super.remove(reason);
        // Unsubscribe from element events — will be wired in Phase 7
        elementSubscriptions.clear();
    }

    // ── Accessors ──

    public BlockPos getBoundArrayPos() {
        return boundArrayPos;
    }

    public UUID getBoundArrayId() {
        return boundArrayId;
    }

    public void setBoundArray(BlockPos pos, UUID arrayId) {
        this.boundArrayPos = pos;
        this.boundArrayId = arrayId;
    }

    // ── Element event hooks (override in subclasses) ──

    /** Called when an element concentration crosses a subscribed threshold */
    protected void onElementThreshold(Object event) {}

    /** Called when an element concentration change exceeds a subscribed minimum */
    protected void onElementChange(Object event) {}
}
