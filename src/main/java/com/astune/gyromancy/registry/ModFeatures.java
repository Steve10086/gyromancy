package com.astune.gyromancy.registry;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.worldgen.SilverTreeFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** World generation feature types owned by Gyromancy. */
public final class ModFeatures {

    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, Gyromancy.MODID);

    public static final DeferredHolder<Feature<?>, SilverTreeFeature> SILVER_TREE =
            FEATURES.register("silver_tree", SilverTreeFeature::new);

    private ModFeatures() {}
}
