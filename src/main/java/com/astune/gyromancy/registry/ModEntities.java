package com.astune.gyromancy.registry;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.api.entity.PseudoEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Registry for pseudo-entity EntityTypes.
 * Pseudo-entities use vanilla Entity loading but have no physics, collision, or AI.
 */
public final class ModEntities {

    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Gyromancy.MODID);

    // EntityTypes will be registered here in Phase 7 as PseudoEntity subclasses are implemented.
    // Example registration pattern:
    //
    // public static final Supplier<EntityType<WispEntity>> WISP =
    //     ENTITIES.register("wisp", () -> EntityType.Builder
    //         .of(WispEntity::new, MobCategory.MISC)
    //         .sized(0.3f, 0.3f)
    //         .updateInterval(10)
    //         .clientTrackingRange(32)
    //         .build(Gyromancy.MODID + ":wisp"));
}
