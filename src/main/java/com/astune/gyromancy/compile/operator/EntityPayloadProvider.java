package com.astune.gyromancy.compile.operator;

import java.util.List;

public interface EntityPayloadProvider extends CompiledOp {
    List<EntityPayload> entityPayloads();
}
