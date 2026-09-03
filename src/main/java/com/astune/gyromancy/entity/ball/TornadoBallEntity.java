package com.astune.gyromancy.entity.ball;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A wind-element magic ball whose behaviour is supplied by its payload ops. */
public final class TornadoBallEntity extends MagicBallEntity {
    public TornadoBallEntity(EntityType<TornadoBallEntity> type, Level level) {
        super(type, level, ElementType.WIND);
    }

    public TornadoBallEntity(Level level, Vec3 pos, Vec3 velocity, Vec3 acceleration, float size) {
        super(ModEntities.TORNADO_BALL.get(), level, ElementType.WIND, velocity, acceleration);
        setBallSize(size);
        setPos(pos);
    }
}
