package com.astune.gyromancy.array.runtime;

import com.astune.gyromancy.compile.operator.CompiledOp;
import net.minecraft.server.level.ServerLevel;

public record OpRuntimeContext(ServerLevel level, CompiledOp op) {}
