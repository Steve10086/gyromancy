package com.astune.gyromancy.compile.operator;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

final class EntityPayloadCodecs {
    private static final Map<ResourceLocation, Codec<? extends EntityPayload>> CODECS = Map.ofEntries(
            Map.entry(ExplosionOp.ID, ExplosionOp.CODEC),
            Map.entry(SmeltOp.ID, SmeltOp.CODEC),
            Map.entry(ElementVolumeOp.ID, ElementVolumeOp.CODEC),
            Map.entry(ElementConversionOp.ID, ElementConversionOp.CODEC),
            Map.entry(ElementOp.ID, ElementOp.CODEC),
            Map.entry(CarryItemsOp.ID, CarryItemsOp.CODEC),
            Map.entry(WaterBurstOp.ID, WaterBurstOp.CODEC),
            Map.entry(BrewingOp.ID, BrewingOp.CODEC),
            Map.entry(MomentumOp.ID, MomentumOp.CODEC),
            Map.entry(RotationOp.ID, RotationOp.CODEC),
            Map.entry(FollowingOp.ID, FollowingOp.CODEC),
            Map.entry(LoopOp.ID, LoopOp.CODEC),
            Map.entry(TornadoAttractionOp.ID, TornadoAttractionOp.CODEC),
            Map.entry(TornadoImpactOp.ID, TornadoImpactOp.CODEC),
            Map.entry(OnDiscardPayload.ID, OnDiscardPayload.CODEC),
            Map.entry(RemoveOnHitOp.ID, OnDiscardPayload.CODEC)
    );

    private EntityPayloadCodecs() {}

    static Optional<Codec<? extends EntityPayload>> codec(ResourceLocation id) {
        return Optional.ofNullable(CODECS.get(id));
    }
}
