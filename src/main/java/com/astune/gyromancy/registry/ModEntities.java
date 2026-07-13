package com.astune.gyromancy.registry;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.entity.FireballEntity;
import com.astune.gyromancy.entity.OldFireballEntity;
import net.minecraft.core.registries.Registries;
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

    public static final Supplier<EntityType<FireballEntity>> FIREBALL =
            ENTITIES.register("fireball", () -> EntityType.Builder
                    .<FireballEntity>of(FireballEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .updateInterval(1)
                    .clientTrackingRange(64)
                    .build(Gyromancy.MODID + ":fireball"));

    public static final Supplier<EntityType<OldFireballEntity>> OLD_FIREBALL =
            ENTITIES.register("old_fireball", () -> EntityType.Builder
                    .<OldFireballEntity>of(OldFireballEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .updateInterval(1)
                    .clientTrackingRange(64)
                    .build(Gyromancy.MODID + ":old_fireball"));
}
