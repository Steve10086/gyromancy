package com.astune.gyromancy.entity;

import com.astune.gyromancy.compile.operator.EntityPayload;
import com.astune.gyromancy.compile.operator.EntityTickContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class MagicEntity extends Entity {
    private static final EntityDataAccessor<CompoundTag> DATA_PAYLOAD =
            SynchedEntityData.defineId(MagicEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<CompoundTag> DATA_PAYLOAD_STATES =
            SynchedEntityData.defineId(MagicEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final String PAYLOAD_KEY = "Payload";

    private final Map<String, Object> runtimeData = new HashMap<>();
    private List<EntityPayload> payload = new ArrayList<>();
    private boolean payloadInitialized;

    protected MagicEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    public final void tick() {
        if (!tickBeforePayload() || !isAlive()) return;
        tickPayloads();
        if (!isAlive()) return;
        tickAfterPayload();
    }

    protected boolean tickBeforePayload() {
        return true;
    }

    protected void tickAfterPayload() {
    }

    public void setPayload(List<? extends EntityPayload> payload) {
        this.payload = new ArrayList<>(payload);
        payloadInitialized = true;
        entityData.set(DATA_PAYLOAD, payloadTag(this.payload));
    }

    protected List<? extends EntityPayload> defaultPayload() {
        return List.of();
    }

    protected Map<String, Object> runtimeData() {
        return runtimeData;
    }

    protected abstract EntityTickContext payloadContext(Map<String, Object> runtimeData);

    private void tickPayloads() {
        initializeDefaultPayload();
        EntityTickContext ctx = payloadContext(runtimeData);
        for (EntityPayload op : payload) {
            if (level().isClientSide && !op.ticksOnClient()) continue;
            op.onEntityTick(ctx);
            if (!isAlive()) return;
        }
        if (!level().isClientSide) syncDirtyClientPayloadStates();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        builder.define(DATA_PAYLOAD, new CompoundTag());
        builder.define(DATA_PAYLOAD_STATES, new CompoundTag());
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_PAYLOAD.equals(key) && level().isClientSide) {
            loadSyncedPayload();
        } else if (DATA_PAYLOAD_STATES.equals(key) && level().isClientSide) {
            loadSyncedPayloadStates();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        initializeDefaultPayload();
        payload.forEach(op -> op.onOwnerRemoved(level()));
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        setPayload(EntityPayload.loadPayloadList(tag, PAYLOAD_KEY, defaultPayload()));
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        initializeDefaultPayload();
        tag.put(PAYLOAD_KEY, EntityPayload.savePayloadList(payload));
    }

    private static CompoundTag payloadTag(List<? extends EntityPayload> payload) {
        CompoundTag tag = new CompoundTag();
        tag.put(PAYLOAD_KEY, EntityPayload.savePayloadList(payload));
        return tag;
    }

    private void loadSyncedPayload() {
        payload = new ArrayList<>(EntityPayload.loadPayloadList(entityData.get(DATA_PAYLOAD), PAYLOAD_KEY, defaultPayload()));
        payloadInitialized = true;
        loadSyncedPayloadStates();
    }

    private void initializeDefaultPayload() {
        if (payloadInitialized) return;
        payload = new ArrayList<>(defaultPayload());
        payloadInitialized = true;
        entityData.set(DATA_PAYLOAD, payloadTag(payload));
    }

    private void loadSyncedPayloadStates() {
        CompoundTag states = entityData.get(DATA_PAYLOAD_STATES);
        for (int i = 0; i < payload.size(); i++) {
            String key = Integer.toString(i);
            payload.get(i).loadClientState(level(), states.contains(key, Tag.TAG_COMPOUND)
                    ? states.getCompound(key)
                    : new CompoundTag());
        }
    }

    private void syncDirtyClientPayloadStates() {
        boolean dirty = false;
        for (EntityPayload op : payload) {
            if (op.hasClientState() && op.consumeClientStateDirty()) dirty = true;
        }
        if (!dirty) return;

        CompoundTag states = new CompoundTag();
        for (int i = 0; i < payload.size(); i++) {
            EntityPayload op = payload.get(i);
            if (op.hasClientState()) states.put(Integer.toString(i), op.saveClientState());
        }
        entityData.set(DATA_PAYLOAD_STATES, states);
    }
}
