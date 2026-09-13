package com.astune.gyromancy.registry;

import com.astune.gyromancy.Gyromancy;
import com.astune.gyromancy.entity.ball.FireballEntity;
import com.astune.gyromancy.entity.ball.IlluminationEntity;
import com.astune.gyromancy.entity.ball.ManaballEntity;
import com.astune.gyromancy.entity.ball.OldFireballEntity;
import com.astune.gyromancy.entity.ball.WaterBallEntity;
import com.astune.gyromancy.entity.ball.IceBallEntity;
import com.astune.gyromancy.entity.ball.DryBallEntity;
import com.astune.gyromancy.entity.ball.TornadoBallEntity;
import com.astune.gyromancy.entity.field.WindFieldEntity;
import com.astune.gyromancy.canvas.CanvasEntity;
import com.astune.gyromancy.entity.projection.ProjectionCanvasEntity;
import com.astune.gyromancy.entity.projection.WandProjectionEntity;
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

    public static final Supplier<EntityType<IlluminationEntity>> ILLUMINATION =
            ENTITIES.register("illumination", () -> EntityType.Builder
                    .<IlluminationEntity>of(IlluminationEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .updateInterval(1)
                    .clientTrackingRange(64)
                    .build(Gyromancy.MODID + ":illumination"));

    public static final Supplier<EntityType<OldFireballEntity>> OLD_FIREBALL =
            ENTITIES.register("old_fireball", () -> EntityType.Builder
                    .<OldFireballEntity>of(OldFireballEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .updateInterval(1)
                    .clientTrackingRange(64)
                    .build(Gyromancy.MODID + ":old_fireball"));

    public static final Supplier<EntityType<ManaballEntity>> MANABALL =
            ENTITIES.register("manaball", () -> EntityType.Builder
                    .<ManaballEntity>of(ManaballEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .updateInterval(1)
                    .clientTrackingRange(64)
                    .build(Gyromancy.MODID + ":manaball"));

    public static final Supplier<EntityType<WaterBallEntity>> WATER_BALL = registerBall("water_ball", WaterBallEntity::new);
    public static final Supplier<EntityType<IceBallEntity>> ICE_BALL = registerBall("ice_ball", IceBallEntity::new);
    public static final Supplier<EntityType<DryBallEntity>> DRY_BALL = registerBall("dry_ball", DryBallEntity::new);
    public static final Supplier<EntityType<TornadoBallEntity>> TORNADO_BALL = registerBall("tornado_ball", TornadoBallEntity::new);

    public static final Supplier<EntityType<WindFieldEntity>> WIND_FIELD =
            ENTITIES.register("wind_field", () -> EntityType.Builder
                    .<WindFieldEntity>of(WindFieldEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .updateInterval(1)
                    .clientTrackingRange(64)
                    .build(Gyromancy.MODID + ":wind_field"));

    public static final Supplier<EntityType<CanvasEntity>> CANVAS =
            ENTITIES.register("canvas", () -> EntityType.Builder
                    .<CanvasEntity>of(CanvasEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .updateInterval(10)
                    .clientTrackingRange(64)
                    .build(Gyromancy.MODID + ":canvas"));

    public static final Supplier<EntityType<ProjectionCanvasEntity>> CANVAS_PROJECTION =
            ENTITIES.register("canvas_projection", () -> EntityType.Builder
                    .<ProjectionCanvasEntity>of(ProjectionCanvasEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .updateInterval(10)
                    .clientTrackingRange(64)
                    .build(Gyromancy.MODID + ":canvas_projection"));

    public static final Supplier<EntityType<WandProjectionEntity>> WAND_PROJECTION =
            ENTITIES.register("wand_projection", () -> EntityType.Builder
                    .<WandProjectionEntity>of(WandProjectionEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .updateInterval(10)
                    .clientTrackingRange(64)
                    .build(Gyromancy.MODID + ":wand_projection"));

    private static <T extends com.astune.gyromancy.entity.ball.MagicBallEntity> Supplier<EntityType<T>> registerBall(
            String name, EntityType.EntityFactory<T> factory) {
        return ENTITIES.register(name, () -> EntityType.Builder.of(factory, MobCategory.MISC)
                .sized(0.5F, 0.5F)
                .updateInterval(1)
                .clientTrackingRange(64)
                .build(Gyromancy.MODID + ":" + name));
    }
}
