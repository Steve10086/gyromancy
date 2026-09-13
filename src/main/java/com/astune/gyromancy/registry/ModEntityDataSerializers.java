package com.astune.gyromancy.registry;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.field.FieldDirection;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModEntityDataSerializers {

    public static final DeferredRegister<EntityDataSerializer<?>> SERIALIZERS =
            DeferredRegister.create(
                    NeoForgeRegistries.ENTITY_DATA_SERIALIZERS,
                    Gyromancy.MODID
            );

    public static final DeferredHolder<
                EntityDataSerializer<?>,
                EntityDataSerializer<FieldDirection>
                > FIELD_DIRECTION =
            SERIALIZERS.register(
                    "field_direction",
                    () -> EntityDataSerializer.forValueType(
                            FieldDirection.STREAM_CODEC
                    )
            );

    public static void register(IEventBus bus) {
        SERIALIZERS.register(bus);
    }

    private ModEntityDataSerializers() {}
}