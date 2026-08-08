package com.astune.gyromancy.entity.field;

import com.astune.gyromancy.entity.MagicEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public abstract class MagicFieldEntity  extends MagicEntity {

    protected MagicFieldEntity(EntityType<?> type, Level level) {
        super(type, level);
    }
}
