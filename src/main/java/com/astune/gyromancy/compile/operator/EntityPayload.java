package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public abstract class EntityPayload {
    /**
     * Same-tick execution phases. An entity's payloads run in phase order, so
     * cooperating ops observe each other's writes within one tick no matter
     * how their list was assembled.
     */
    public enum TickPhase {
        /** Produces resources later phases spend (for example element into mana). */
        PRODUCE,
        /** Spends produced resources on the owner (for example mana into volume). */
        GROW,
        /** Repurposes what the spending phase left behind (for example mana into element). */
        CONVERT,
        /** Reactions, triggers and utility payloads. The default phase. */
        EFFECT
    }

    public abstract ResourceLocation typeId();

    protected abstract Codec<? extends EntityPayload> codec();

    /** The phase this payload runs in within a single entity tick. */
    public TickPhase tickPhase() {
        return TickPhase.EFFECT;
    }

    /** Returns a stable phase-ordered copy of the supplied payloads. */
    public static List<EntityPayload> orderedByTickPhase(Collection<? extends EntityPayload> payload) {
        List<EntityPayload> ordered = new ArrayList<>(payload);
        ordered.sort(Comparator.comparingInt(op -> op.tickPhase().ordinal()));
        return ordered;
    }

    public void onEntityTick(EntityTickContext ctx) {}

    public boolean ticksOnClient() {
        return false;
    }

    public boolean hasClientState() {
        return false;
    }

    public boolean consumeClientStateDirty() {
        return false;
    }

    public CompoundTag saveClientState() {
        return new CompoundTag();
    }

    public void loadClientState(Level level, CompoundTag tag) {}

    public void onOwnerRemoved(Level level) {}

    public void onOwnerRemoved(Level level, Entity owner) {
        onOwnerRemoved(level);
    }

    public void bindToArray(UUID arrayId) {}

    /** Server-aware binding hook for payloads which need to rebuild structure. */
    public void bindToArray(ServerLevel level, UUID arrayId) {
        bindToArray(arrayId);
    }

    public CompoundTag savePayload() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", typeId().toString());
        encodeWithOwnCodec().ifPresent(data -> tag.put("data", data));
        return tag;
    }

    public static Optional<EntityPayload> loadPayload(CompoundTag tag) {
        if (!tag.contains("type")) return Optional.empty();
        ResourceLocation type = ResourceLocation.parse(tag.getString("type"));
        return EntityPayloadCodecs.codec(type).flatMap(codec -> codec.parse(NbtOps.INSTANCE,
                        tag.contains("data") ? tag.get("data") : new CompoundTag())
                .resultOrPartial(error -> Gyromancy.LOGGER.warn("Failed to load entity payload {}: {}", type, error)));
    }

    public static ListTag savePayloadList(List<? extends EntityPayload> payload) {
        ListTag list = new ListTag();
        payload.forEach(op -> list.add(op.savePayload()));
        return list;
    }

    public static List<EntityPayload> loadPayloadList(CompoundTag tag, String key, List<? extends EntityPayload> fallback) {
        if (!tag.contains(key, Tag.TAG_LIST)) return List.copyOf(fallback);
        List<EntityPayload> payload = new ArrayList<>();
        for (Tag value : tag.getList(key, Tag.TAG_COMPOUND)) {
            if (value instanceof CompoundTag entry) loadPayload(entry).ifPresent(payload::add);
        }
        return List.copyOf(payload);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Optional<Tag> encodeWithOwnCodec() {
        return ((Codec)codec()).encodeStart(NbtOps.INSTANCE, this)
                .resultOrPartial(error -> Gyromancy.LOGGER.warn("Failed to save entity payload {}: {}", typeId(), error))
                .map(Tag.class::cast);
    }
}
