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
 * <p>The configured feature uses {@link SilverTreeFeature} for both sapling growth
 * and natural generation.</p>
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
