package com.astune.gyromancy.entity.ball;

import com.astune.gyromancy.api.element.ElementType;
import com.astune.gyromancy.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A projectile mana ball. It is intentionally not a stationary magic field. */
public class ManaballEntity extends MagicBallEntity {
    public ManaballEntity(EntityType<ManaballEntity> type, Level level) {
        super(type, level, ElementType.MANA);
    }

    public ManaballEntity(Level level, Vec3 pos, Vec3 velocity, double arrowSizeSum,
                          double liftDirection, Vec3 acceleration, float size) {
        super(ModEntities.MANABALL.get(), level, ElementType.MANA,
                calculateLaunchVelocity(velocity, arrowSizeSum, liftDirection), acceleration);
        setBallSize(size);
        setPos(pos);
    }

    private static Vec3 calculateLaunchVelocity(Vec3 velocity, double arrowSizeSum, double liftDirection) {
        double speed = velocity.length();
        double lift = ((arrowSizeSum - speed) + 0.2 * speed) * liftDirection;
        return velocity.add(0.0, lift, 0.0);
    }
}
