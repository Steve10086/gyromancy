package com.astune.gyromancy.compile.operator;

import com.astune.gyromancy.Gyromancy;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class OnEntityTickOp {
    public abstract void onEntityTick(EntityTickContext ctx);

    public abstract ResourceLocation typeId();

    protected abstract Codec<? extends OnEntityTickOp> codec();

    public CompoundTag savePayload() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", typeId().toString());
        encodeWithOwnCodec().ifPresent(data -> tag.put("data", data));
        return tag;
    }

    public static Optional<OnEntityTickOp> loadPayload(CompoundTag tag) {
        if (!tag.contains("type")) return Optional.empty();
        ResourceLocation type = ResourceLocation.parse(tag.getString("type"));
        return OnEntityTickOpCodecs.codec(type).flatMap(codec -> codec.parse(NbtOps.INSTANCE,
                        tag.contains("data") ? tag.get("data") : new CompoundTag())
                .resultOrPartial(error -> Gyromancy.LOGGER.warn("Failed to load payload op {}: {}", type, error)));
    }

    public static ListTag savePayloadList(List<OnEntityTickOp> payload) {
        ListTag list = new ListTag();
        payload.forEach(op -> list.add(op.savePayload()));
        return list;
    }

    public static List<OnEntityTickOp> loadPayloadList(CompoundTag tag, String key, List<OnEntityTickOp> fallback) {
        if (!tag.contains(key, Tag.TAG_LIST)) return fallback;
        List<OnEntityTickOp> payload = new ArrayList<>();
        for (Tag value : tag.getList(key, Tag.TAG_COMPOUND)) {
            if (value instanceof CompoundTag entry) loadPayload(entry).ifPresent(payload::add);
        }
        return List.copyOf(payload);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Optional<Tag> encodeWithOwnCodec() {
        return ((Codec)codec()).encodeStart(NbtOps.INSTANCE, this)
                .resultOrPartial(error -> Gyromancy.LOGGER.warn("Failed to save payload op {}: {}", typeId(), error))
                .map(Tag.class::cast);
    }
}
