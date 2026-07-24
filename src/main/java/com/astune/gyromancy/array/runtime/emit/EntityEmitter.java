package com.astune.gyromancy.array.runtime.emit;

import com.astune.gyromancy.api.array.ArrayObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public final class EntityEmitter implements Emitter {
    public static final EntityEmitter INSTANCE = new EntityEmitter();

    private EntityEmitter() {}

    @Override
    public void emit(ServerLevel level, ResourceLocation emitType, Entity entity, EmitResult result) {
        level.addFreshEntity(entity);
        result.add(new EmittedObject(emitType, ArrayObject.EntityRef.of(entity)));
    }
}
