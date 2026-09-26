package com.astune.gyromancy.worldgen;

import com.astune.gyromancy.Gyromancy;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

import java.util.Optional;

/**
 * Growth entry point for the silver tree.
 *
 * <p>The placement shape is not designed yet; {@link #SILVER_TREE} currently
 * points at an oak-shaped configured feature that only swaps in silver log and
 * leaf blocks. Replace {@code data/gyromancy/worldgen/configured_feature/silver_tree.json}
 * (and this class if the grower needs variants) once the final model exists.</p>
 */
public final class SilverTreeGrower {

    public static final ResourceKey<ConfiguredFeature<?, ?>> SILVER_TREE = ResourceKey.create(
            Registries.CONFIGURED_FEATURE,
            ResourceLocation.fromNamespaceAndPath(Gyromancy.MODID, "silver_tree"));

    public static final TreeGrower SILVER = new TreeGrower(
            "gyromancy_silver",
            Optional.empty(),
            Optional.of(SILVER_TREE),
            Optional.empty());

    private SilverTreeGrower() {}
}
