package com.astune.gyromancy.registry;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.ink.InkType;
import com.astune.gyromancy.api.ink.PenProperties;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * DataComponentType registrations for Gyromancy items.
 * Data components store ink type, pen properties, and other item state.
 */
public final class ModDataComponents {

    private ModDataComponents() {}

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Gyromancy.MODID);

    /** Stores the ink type identifier on ink bottle items */
    public static final Supplier<DataComponentType<ResourceLocation>> INK_TYPE =
            DATA_COMPONENTS.register("ink_type", () ->
                    DataComponentType.<ResourceLocation>builder()
                            .persistent(ResourceLocation.CODEC)
                            .networkSynchronized(ResourceLocation.STREAM_CODEC)
                            .build()
            );

    /** Stores pen properties on pen/brush items */
    public static final Supplier<DataComponentType<PenProperties>> PEN_PROPERTIES =
            DATA_COMPONENTS.register("pen_properties", () ->
                    DataComponentType.<PenProperties>builder()
                            .persistent(PenProperties.CODEC)
                            .networkSynchronized(ByteBufCodecs.fromCodec(PenProperties.CODEC))
                            .build()
            );

    /** Remaining ink charges on a pen item */
    public static final Supplier<DataComponentType<Integer>> INK_REMAINING =
            DATA_COMPONENTS.register("ink_remaining", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build()
            );
}
