package com.astune.gyromancy.api.entity;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Type identifier for pseudo-entities. Not to be confused with {@code net.minecraft.world.entity.EntityType};
 * this is a lightweight record that binds a factory to a resource location for use in the array effect system.
 */
public record PseudoEntityType(
        ResourceLocation id,
        Factory factory
) {
    /**
     * Factory for creating pseudo-entity instances from array context.
     */
    @FunctionalInterface
    public interface Factory {
        PseudoEntity create(Level level, BlockPos arrayPos, UUID arrayId, Map<String, Object> params);
    }
}
