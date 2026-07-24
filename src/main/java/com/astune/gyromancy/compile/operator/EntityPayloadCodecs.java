package com.astune.gyromancy.compile.operator;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

final class EntityPayloadCodecs {
    private static final Map<ResourceLocation, Codec<? extends EntityPayload>> CODECS = Map.of(
            ExplosionOp.ID, ExplosionOp.CODEC,
            SmeltOp.ID, SmeltOp.CODEC,
            ElementConversionOp.ID, ElementConversionOp.CODEC,
            ElementOp.ID, ElementOp.CODEC,
            CarryItemsOp.ID, CarryItemsOp.CODEC,
            WaterBurstOp.ID, WaterBurstOp.CODEC,
            BrewingOp.ID, BrewingOp.CODEC
    );

    private EntityPayloadCodecs() {}

    static Optional<Codec<? extends EntityPayload>> codec(ResourceLocation id) {
        return Optional.ofNullable(CODECS.get(id));
    }
}
