package com.astune.gyromancy.array.runtime.emit;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public interface Emitter {
    void emit(ServerLevel level, ResourceLocation emitType, Entity entity, EmitResult result);
}
