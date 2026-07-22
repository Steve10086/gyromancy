package com.astune.gyromancy.compile.operator;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

final class OnEntityTickOpCodecs {
    private static final Map<ResourceLocation, Codec<? extends OnEntityTickOp>> CODECS = Map.of(
            ExplosionOp.ID, ExplosionOp.CODEC,
            SmeltOp.ID, SmeltOp.CODEC,
            ElementConversionOp.ID, ElementConversionOp.CODEC
    );

    private OnEntityTickOpCodecs() {}

    static Optional<Codec<? extends OnEntityTickOp>> codec(ResourceLocation id) {
        return Optional.ofNullable(CODECS.get(id));
    }
}
