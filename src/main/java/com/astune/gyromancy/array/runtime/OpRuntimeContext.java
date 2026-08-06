package com.astune.gyromancy.array.runtime;

import com.astune.gyromancy.compile.operator.CompiledOp;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public record OpRuntimeContext(ServerLevel level, CompiledOp op, Vec3 origin, Vec3 normal) {
    public OpRuntimeContext(ServerLevel level, CompiledOp op) {
        this(level, op, null, null);
    }

    public OpRuntimeContext at(Vec3 origin, Vec3 normal) {
        return new OpRuntimeContext(level, op, origin, normal);
    }
}
